package com.rideflow.drivermatchingservice.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.drivermatchingservice.kafka.event.PriceCalculatedEvent;
import com.rideflow.drivermatchingservice.kafka.event.RideCancelledEvent;
import com.rideflow.drivermatchingservice.service.DriverMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverMatchingEventConsumer {

    private final DriverMatchingService driverMatchingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "price.calculated", groupId = "driver-matching-service")
    public void handlePriceCalculated(String message) {
        try {
            PriceCalculatedEvent event = objectMapper.readValue(message, PriceCalculatedEvent.class);
            log.info("Received price.calculated event: rideId={}", event.getRideId());
            driverMatchingService.processRideForMatching(event);
        } catch (Exception e) {
            log.error("Error processing price.calculated event", e);
            throw new RuntimeException("Failed to process price.calculated event", e);
        }
    }

    @KafkaListener(topics = "ride.cancelled", groupId = "driver-matching-service")
    public void handleRideCancelled(String message) {
        try {
            RideCancelledEvent event = objectMapper.readValue(message, RideCancelledEvent.class);
            log.info("Received ride.cancelled event: rideId={}, cancelledBy={}", event.getRideId(), event.getCancelledBy());
            driverMatchingService.handleRideCancellation(event);
        } catch (Exception e) {
            log.error("Error processing ride.cancelled event", e);
            throw new RuntimeException("Failed to process ride.cancelled event", e);
        }
    }
}
