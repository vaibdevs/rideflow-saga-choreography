package com.rideflow.paymentservice.service;

import com.rideflow.paymentservice.domain.Payment;
import com.rideflow.paymentservice.domain.PaymentStatus;
import com.rideflow.paymentservice.gateway.PaymentGateway;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeRequest;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeResponse;
import com.rideflow.paymentservice.gateway.PaymentGateway.RefundRequest;
import com.rideflow.paymentservice.gateway.PaymentGateway.RefundResponse;
import com.rideflow.paymentservice.kafka.event.*;
import com.rideflow.paymentservice.kafka.producer.PaymentEventProducer;
import com.rideflow.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentEventProducer eventProducer;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    // --- processPayment tests ---

    @Test
    void processPayment_newRide_chargesAndSavesPayment() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("250.00");

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .driverId(UUID.randomUUID())
                .fareActual(fare)
                .timestamp(LocalDateTime.now())
                .build();

        when(paymentRepository.existsByRideId(rideId)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(paymentGateway.charge(any(ChargeRequest.class)))
                .thenReturn(new ChargeResponse(true, "txn_123", null));

        paymentService.processPayment(event);

        // save called twice: once for PENDING, once for SUCCESS
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        Payment pendingPayment = paymentCaptor.getAllValues().get(0);
        Payment successPayment = paymentCaptor.getAllValues().get(1);

        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(successPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(successPayment.getGatewayTxnId()).isEqualTo("txn_123");

        verify(eventProducer).publishPaymentSuccess(any(PaymentSuccessEvent.class));
    }

    @Test
    void processPayment_duplicateRideId_doesNotChargeAgain() {
        UUID rideId = UUID.randomUUID();

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(rideId)
                .riderId(UUID.randomUUID())
                .fareActual(new BigDecimal("200.00"))
                .timestamp(LocalDateTime.now())
                .build();

        when(paymentRepository.existsByRideId(rideId)).thenReturn(true);

        paymentService.processPayment(event);

        verify(paymentGateway, never()).charge(any(ChargeRequest.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_gatewayFailure_savesFailedPaymentAndPublishesEvent() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("300.00");

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .fareActual(fare)
                .timestamp(LocalDateTime.now())
                .build();

        when(paymentRepository.existsByRideId(rideId)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(paymentGateway.charge(any(ChargeRequest.class)))
                .thenReturn(new ChargeResponse(false, null, "Insufficient funds"));

        paymentService.processPayment(event);

        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        Payment failedPayment = paymentCaptor.getAllValues().get(1);
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        verify(eventProducer).publishPaymentFailed(any(PaymentFailedEvent.class));
        verify(eventProducer, never()).publishPaymentSuccess(any(PaymentSuccessEvent.class));
    }

    // --- processRefund tests ---

    @Test
    void processRefund_driverCancelledAfterStart_issuesFullRefund() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        RideCancelledEvent event = RideCancelledEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .driverId(UUID.randomUUID())
                .cancelledBy("DRIVER")
                .previousStatus("STARTED")
                .createdAt(LocalDateTime.now().minusMinutes(15))
                .timestamp(LocalDateTime.now())
                .build();

        Payment existingPayment = Payment.builder()
                .id(paymentId)
                .rideId(rideId)
                .riderId(riderId)
                .amount(new BigDecimal("250.00"))
                .status(PaymentStatus.SUCCESS)
                .gatewayTxnId("txn_original")
                .idempotencyKey("charge_" + rideId)
                .build();

        when(paymentRepository.findByRideId(rideId)).thenReturn(Optional.of(existingPayment));
        when(paymentGateway.refund(any(RefundRequest.class)))
                .thenReturn(new RefundResponse(true, "refund_456", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.processRefund(event);

        verify(paymentGateway).refund(any(RefundRequest.class));
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventProducer).publishPaymentRefunded(any(PaymentRefundedEvent.class));
    }

    @Test
    void processRefund_driverCancelledWhileAssigned_noRefundIssued() {
        RideCancelledEvent event = RideCancelledEvent.builder()
                .rideId(UUID.randomUUID())
                .riderId(UUID.randomUUID())
                .cancelledBy("DRIVER")
                .previousStatus("DRIVER_ASSIGNED")
                .timestamp(LocalDateTime.now())
                .build();

        paymentService.processRefund(event);

        verify(paymentGateway, never()).refund(any(RefundRequest.class));
        verify(eventProducer, never()).publishPaymentRefunded(any(PaymentRefundedEvent.class));
    }

    @Test
    void processRefund_riderCancelledLate_chargesCancellationFee() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();

        RideCancelledEvent event = RideCancelledEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .cancelledBy("RIDER")
                .previousStatus("DRIVER_ASSIGNED")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .timestamp(LocalDateTime.now())
                .build();

        when(paymentRepository.existsByRideId(rideId)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(paymentGateway.charge(any(ChargeRequest.class)))
                .thenReturn(new ChargeResponse(true, "txn_cancel_fee", null));

        paymentService.processRefund(event);

        verify(paymentGateway).charge(argThat(request ->
                request.amount().compareTo(new BigDecimal("50")) == 0));
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        Payment chargedPayment = paymentCaptor.getAllValues().get(1);
        assertThat(chargedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(chargedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("50"));
    }
}
