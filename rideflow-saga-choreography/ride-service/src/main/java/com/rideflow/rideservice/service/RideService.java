package com.rideflow.rideservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.rideservice.domain.*;
import com.rideflow.rideservice.dto.CancelRideRequest;
import com.rideflow.rideservice.dto.CreateRideRequest;
import com.rideflow.rideservice.dto.RideResponse;
import com.rideflow.rideservice.exception.InvalidRideTransitionException;
import com.rideflow.rideservice.exception.RideNotFoundException;
import com.rideflow.rideservice.kafka.event.*;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import com.rideflow.rideservice.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RideResponse createRide(CreateRideRequest request) {
        Ride ride = Ride.builder()
                .riderId(request.getRiderId())
                .status(RideStatus.REQUESTED)
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropLat(request.getDropLat())
                .dropLng(request.getDropLng())
                .build();

        ride = rideRepository.save(ride);
        log.info("Ride created with id={}, status={}", ride.getId(), ride.getStatus());

        RideRequestedEvent event = RideRequestedEvent.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .pickupLat(ride.getPickupLat())
                .pickupLng(ride.getPickupLng())
                .dropLat(ride.getDropLat())
                .dropLng(ride.getDropLng())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(ride.getId().toString(), "ride.requested", event);
        return toResponse(ride);
    }

    public RideResponse getRide(UUID rideId) {
        Ride ride = findRideOrThrow(rideId);
        return toResponse(ride);
    }

    @Transactional
    public RideResponse cancelRide(UUID rideId, CancelRideRequest request) {
        Ride ride = findRideOrThrow(rideId);
        String previousStatus = ride.getStatus().name();

        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.CANCELLED);

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledBy(request.getCancelledBy());
        ride = rideRepository.save(ride);
        log.info("Ride cancelled: rideId={}, cancelledBy={}", rideId, request.getCancelledBy());

        RideCancelledEvent event = RideCancelledEvent.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .cancelledBy(request.getCancelledBy())
                .previousStatus(previousStatus)
                .createdAt(ride.getCreatedAt())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(ride.getId().toString(), "ride.cancelled", event);
        return toResponse(ride);
    }

    @Transactional
    public RideResponse startRide(UUID rideId) {
        Ride ride = findRideOrThrow(rideId);
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.STARTED);

        ride.setStatus(RideStatus.STARTED);
        ride = rideRepository.save(ride);
        log.info("Ride started: rideId={}", rideId);

        RideStartedEvent event = RideStartedEvent.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .pickupLat(ride.getPickupLat())
                .pickupLng(ride.getPickupLng())
                .dropLat(ride.getDropLat())
                .dropLng(ride.getDropLng())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(ride.getId().toString(), "ride.started", event);
        return toResponse(ride);
    }

    @Transactional
    public RideResponse completeRide(UUID rideId) {
        Ride ride = findRideOrThrow(rideId);
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.COMPLETED);

        ride.setStatus(RideStatus.COMPLETED);
        ride.setFareActual(ride.getFareEstimate() != null ? ride.getFareEstimate() : BigDecimal.ZERO);
        ride = rideRepository.save(ride);
        log.info("Ride completed: rideId={}, fareActual={}", rideId, ride.getFareActual());

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .fareActual(ride.getFareActual())
                .timestamp(LocalDateTime.now())
                .build();

        saveOutboxEvent(ride.getId().toString(), "ride.completed", event);
        return toResponse(ride);
    }

    @Transactional
    public void handleDriverAssigned(DriverAssignedEvent event) {
        Ride ride = findRideOrThrow(event.getRideId());
        if (ride.getStatus() == RideStatus.DRIVER_ASSIGNED) {
            log.info("Ride {} already has driver assigned, skipping duplicate event", event.getRideId());
            return;
        }
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.DRIVER_ASSIGNED);
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(event.getDriverId());
        rideRepository.save(ride);
        log.info("Driver assigned to ride: rideId={}, driverId={}", event.getRideId(), event.getDriverId());
    }

    @Transactional
    public void handleDriverUnavailable(DriverUnavailableEvent event) {
        Ride ride = findRideOrThrow(event.getRideId());
        if (ride.getStatus() == RideStatus.CANCELLED) {
            log.info("Ride {} already cancelled, skipping duplicate event", event.getRideId());
            return;
        }
        try {
            RideStateMachine.validateTransition(ride.getStatus(), RideStatus.CANCELLED);
            ride.setStatus(RideStatus.CANCELLED);
            ride.setCancelledBy("SYSTEM");
            rideRepository.save(ride);
            log.info("Ride cancelled due to no available drivers: rideId={}", event.getRideId());
        } catch (InvalidRideTransitionException e) {
            log.warn("Cannot cancel ride {} from status {}: {}", event.getRideId(), ride.getStatus(), e.getMessage());
        }
    }

    @Transactional
    public void handlePriceCalculated(PriceCalculatedEvent event) {
        Ride ride = findRideOrThrow(event.getRideId());
        if (ride.getStatus() == RideStatus.PRICE_CALCULATED) {
            log.info("Ride {} already price calculated, skipping duplicate event", event.getRideId());
            return;
        }
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.PRICE_CALCULATED);
        ride.setStatus(RideStatus.PRICE_CALCULATED);
        ride.setFareEstimate(event.getFareEstimate());
        rideRepository.save(ride);
        log.info("Price calculated for ride: rideId={}, fare={}", event.getRideId(), event.getFareEstimate());
    }

    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        Ride ride = findRideOrThrow(event.getRideId());
        if (ride.getStatus() == RideStatus.PAID) {
            log.info("Ride {} already paid, skipping duplicate event", event.getRideId());
            return;
        }
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.PAID);
        ride.setStatus(RideStatus.PAID);
        rideRepository.save(ride);
        log.info("Payment successful for ride: rideId={}", event.getRideId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Ride ride = findRideOrThrow(event.getRideId());
        if (ride.getStatus() == RideStatus.PAYMENT_FAILED) {
            log.info("Ride {} already marked payment failed, skipping duplicate event", event.getRideId());
            return;
        }
        RideStateMachine.validateTransition(ride.getStatus(), RideStatus.PAYMENT_FAILED);
        ride.setStatus(RideStatus.PAYMENT_FAILED);
        rideRepository.save(ride);
        log.warn("Payment failed for ride: rideId={}, reason={}", event.getRideId(), event.getReason());
    }

    private Ride findRideOrThrow(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private void saveOutboxEvent(String aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }

    private RideResponse toResponse(Ride ride) {
        return RideResponse.builder()
                .id(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .status(ride.getStatus())
                .pickupLat(ride.getPickupLat())
                .pickupLng(ride.getPickupLng())
                .dropLat(ride.getDropLat())
                .dropLng(ride.getDropLng())
                .fareEstimate(ride.getFareEstimate())
                .fareActual(ride.getFareActual())
                .cancelledBy(ride.getCancelledBy())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .build();
    }
}
