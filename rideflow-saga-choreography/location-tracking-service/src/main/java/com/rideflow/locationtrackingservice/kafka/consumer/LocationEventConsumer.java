package com.rideflow.locationtrackingservice.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocationEventConsumer {

    @KafkaListener(topics = "ride.started", groupId = "location-tracking-service")
    public void handleRideStarted(String message) {
        log.info("Received ride.started event - location tracking is now active: {}", message);
    }
}
