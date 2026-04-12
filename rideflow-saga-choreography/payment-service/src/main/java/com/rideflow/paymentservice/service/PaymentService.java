package com.rideflow.paymentservice.service;

import com.rideflow.paymentservice.domain.Payment;
import com.rideflow.paymentservice.domain.PaymentStatus;
import com.rideflow.paymentservice.gateway.PaymentGateway;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeRequest;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeResponse;
import com.rideflow.paymentservice.kafka.event.*;
import com.rideflow.paymentservice.kafka.producer.PaymentEventProducer;
import com.rideflow.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal CANCELLATION_FEE = new BigDecimal("50");
    private static final long FREE_CANCEL_WINDOW_MINUTES = 5;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public void processPayment(RideCompletedEvent event) {
        // Idempotency check — do not charge again if payment exists for this ride
        if (paymentRepository.existsByRideId(event.getRideId())) {
            log.info("Payment already exists for rideId={}, skipping duplicate", event.getRideId());
            return;
        }

        String idempotencyKey = "charge_" + event.getRideId().toString();
        BigDecimal amount = event.getFareActual() != null ? event.getFareActual() : BigDecimal.ZERO;

        Payment payment = Payment.builder()
                .rideId(event.getRideId())
                .riderId(event.getRiderId())
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Second safety net — UNIQUE constraint on ride_id
            log.warn("Duplicate payment attempt caught by DB constraint: rideId={}", event.getRideId());
            return;
        }

        ChargeResponse response = paymentGateway.charge(
                new ChargeRequest(idempotencyKey, amount, "INR", "Ride fare for " + event.getRideId()));

        if (response.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayTxnId(response.transactionId());
            paymentRepository.save(payment);

            eventProducer.publishPaymentSuccess(PaymentSuccessEvent.builder()
                    .paymentId(payment.getId())
                    .rideId(event.getRideId())
                    .riderId(event.getRiderId())
                    .amount(amount)
                    .gatewayTxnId(response.transactionId())
                    .timestamp(LocalDateTime.now())
                    .build());

            log.info("Payment successful: rideId={}, txnId={}", event.getRideId(), response.transactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            eventProducer.publishPaymentFailed(PaymentFailedEvent.builder()
                    .paymentId(payment.getId())
                    .rideId(event.getRideId())
                    .riderId(event.getRiderId())
                    .amount(amount)
                    .reason(response.failureReason())
                    .timestamp(LocalDateTime.now())
                    .build());

            log.warn("Payment failed: rideId={}, reason={}", event.getRideId(), response.failureReason());
        }
    }

    @Transactional
    public void processRefund(RideCancelledEvent event) {
        String cancelledBy = event.getCancelledBy();
        String previousStatus = event.getPreviousStatus();

        log.info("Processing refund for rideId={}, cancelledBy={}, previousStatus={}",
                event.getRideId(), cancelledBy, previousStatus);

        // If ride was in DRIVER_ASSIGNED state and driver cancels → NO refund (rider never paid)
        if ("DRIVER".equals(cancelledBy) && "DRIVER_ASSIGNED".equals(previousStatus)) {
            log.info("No refund needed: driver cancelled in DRIVER_ASSIGNED state, rideId={}", event.getRideId());
            return;
        }

        // If driver cancelled after ride STARTED → full refund
        if ("DRIVER".equals(cancelledBy) && "STARTED".equals(previousStatus)) {
            issueRefund(event, event.getRideId(), "Driver cancelled during active ride");
            return;
        }

        // If rider cancels after free window (5+ min since ride creation) → charge cancellation fee
        if ("RIDER".equals(cancelledBy)) {
            if (event.getCreatedAt() != null) {
                long minutesSinceCreation = ChronoUnit.MINUTES.between(event.getCreatedAt(), LocalDateTime.now());
                if (minutesSinceCreation >= FREE_CANCEL_WINDOW_MINUTES) {
                    chargeCancellationFee(event);
                    return;
                }
            }
            log.info("Rider cancelled within free window, no charge: rideId={}", event.getRideId());
        }
    }

    private void issueRefund(RideCancelledEvent event, UUID rideId, String reason) {
        Payment payment = paymentRepository.findByRideId(rideId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCESS) {
            log.info("No successful payment to refund for rideId={}", rideId);
            return;
        }

        PaymentGateway.RefundResponse response = paymentGateway.refund(
                new PaymentGateway.RefundRequest(payment.getGatewayTxnId(), payment.getAmount(), reason));

        if (response.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            eventProducer.publishPaymentRefunded(PaymentRefundedEvent.builder()
                    .paymentId(payment.getId())
                    .rideId(rideId)
                    .riderId(event.getRiderId())
                    .refundAmount(payment.getAmount())
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build());

            log.info("Refund issued: rideId={}, amount={}", rideId, payment.getAmount());
        } else {
            log.error("Refund failed: rideId={}, reason={}", rideId, response.failureReason());
        }
    }

    private void chargeCancellationFee(RideCancelledEvent event) {
        if (paymentRepository.existsByRideId(event.getRideId())) {
            log.info("Cancellation fee already charged for rideId={}", event.getRideId());
            return;
        }

        String idempotencyKey = "cancel_fee_" + event.getRideId().toString();

        Payment payment = Payment.builder()
                .rideId(event.getRideId())
                .riderId(event.getRiderId())
                .amount(CANCELLATION_FEE)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate cancellation fee caught by DB constraint: rideId={}", event.getRideId());
            return;
        }

        ChargeResponse response = paymentGateway.charge(
                new ChargeRequest(idempotencyKey, CANCELLATION_FEE, "INR",
                        "Cancellation fee for ride " + event.getRideId()));

        if (response.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayTxnId(response.transactionId());
            paymentRepository.save(payment);
            log.info("Cancellation fee charged: rideId={}, amount={}", event.getRideId(), CANCELLATION_FEE);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Failed to charge cancellation fee: rideId={}", event.getRideId());
        }
    }
}
