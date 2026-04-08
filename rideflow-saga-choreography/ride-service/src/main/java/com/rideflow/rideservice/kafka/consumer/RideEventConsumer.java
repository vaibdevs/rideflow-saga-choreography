package com.rideflow.rideservice.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.rideservice.kafka.event.DriverAssignedEvent;
import com.rideflow.rideservice.kafka.event.DriverUnavailableEvent;
import com.rideflow.rideservice.kafka.event.PaymentFailedEvent;
import com.rideflow.rideservice.kafka.event.PaymentSuccessEvent;
import com.rideflow.rideservice.kafka.event.PriceCalculatedEvent;
import com.rideflow.rideservice.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventConsumer {

    private final RideService rideService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "price.calculated", groupId = "ride-service")
    public void handlePriceCalculated(String message) {
        try {
            PriceCalculatedEvent event = objectMapper.readValue(message, PriceCalculatedEvent.class);
            log.info("Received price.calculated event: rideId={}", event.getRideId());
            rideService.handlePriceCalculated(event);
        } catch (Exception e) {
            log.error("Error processing price.calculated event", e);
            throw new RuntimeException("Failed to process price.calculated event", e);
        }
    }

    @KafkaListener(topics = "driver.assigned", groupId = "ride-service")
    public void handleDriverAssigned(String message) {
        try {
            DriverAssignedEvent event = objectMapper.readValue(message, DriverAssignedEvent.class);
            log.info("Received driver.assigned event: rideId={}, driverId={}", event.getRideId(), event.getDriverId());
            rideService.handleDriverAssigned(event);
        } catch (Exception e) {
            log.error("Error processing driver.assigned event", e);
            throw new RuntimeException("Failed to process driver.assigned event", e);
        }
    }

    @KafkaListener(topics = "driver.unavailable", groupId = "ride-service")
    public void handleDriverUnavailable(String message) {
        try {
            DriverUnavailableEvent event = objectMapper.readValue(message, DriverUnavailableEvent.class);
            log.info("Received driver.unavailable event: rideId={}", event.getRideId());
            rideService.handleDriverUnavailable(event);
        } catch (Exception e) {
            log.error("Error processing driver.unavailable event", e);
            throw new RuntimeException("Failed to process driver.unavailable event", e);
        }
    }

    @KafkaListener(topics = "payment.success", groupId = "ride-service")
    public void handlePaymentSuccess(String message) {
        try {
            PaymentSuccessEvent event = objectMapper.readValue(message, PaymentSuccessEvent.class);
            log.info("Received payment.success event: rideId={}", event.getRideId());
            rideService.handlePaymentSuccess(event);
        } catch (Exception e) {
            log.error("Error processing payment.success event", e);
            throw new RuntimeException("Failed to process payment.success event", e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "ride-service")
    public void handlePaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            log.info("Received payment.failed event: rideId={}", event.getRideId());
            rideService.handlePaymentFailed(event);
        } catch (Exception e) {
            log.error("Error processing payment.failed event", e);
            throw new RuntimeException("Failed to process payment.failed event", e);
        }
    }
}
