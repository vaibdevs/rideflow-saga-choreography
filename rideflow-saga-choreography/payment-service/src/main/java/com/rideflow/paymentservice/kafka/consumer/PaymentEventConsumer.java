package com.rideflow.paymentservice.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.paymentservice.kafka.event.RideCancelledEvent;
import com.rideflow.paymentservice.kafka.event.RideCompletedEvent;
import com.rideflow.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ride.completed", groupId = "payment-service")
    public void handleRideCompleted(String message) {
        try {
            RideCompletedEvent event = objectMapper.readValue(message, RideCompletedEvent.class);
            log.info("Received ride.completed event: rideId={}, fare={}", event.getRideId(), event.getFareActual());
            paymentService.processPayment(event);
        } catch (Exception e) {
            log.error("Error processing ride.completed event", e);
            throw new RuntimeException("Failed to process ride.completed event", e);
        }
    }

    @KafkaListener(topics = "ride.cancelled", groupId = "payment-service")
    public void handleRideCancelled(String message) {
        try {
            RideCancelledEvent event = objectMapper.readValue(message, RideCancelledEvent.class);
            log.info("Received ride.cancelled event: rideId={}, cancelledBy={}", event.getRideId(), event.getCancelledBy());
            paymentService.processRefund(event);
        } catch (Exception e) {
            log.error("Error processing ride.cancelled event", e);
            throw new RuntimeException("Failed to process ride.cancelled event", e);
        }
    }
}
