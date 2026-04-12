package com.rideflow.notificationservice.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.notificationservice.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationRouter notificationRouter;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "driver.assigned", groupId = "notification-service")
    public void handleDriverAssigned(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received driver.assigned event: rideId={}", node.get("rideId").asText());

            Map<String, String> vars = new HashMap<>();
            vars.put("driverName", getField(node, "driverName"));
            vars.put("eta", getField(node, "etaMinutes"));
            vars.put("vehicle", getField(node, "vehicleNumber") + " (" + getField(node, "vehicleType") + ")");

            notificationRouter.notifyBoth("driver.assigned",
                    getField(node, "riderId"),
                    getField(node, "driverId"),
                    vars);
        } catch (Exception e) {
            log.error("Error processing driver.assigned notification", e);
            throw new RuntimeException("Failed to process driver.assigned notification", e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "driver.unavailable", groupId = "notification-service")
    public void handleDriverUnavailable(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received driver.unavailable event: rideId={}", node.get("rideId").asText());

            notificationRouter.notifyRider("driver.unavailable",
                    getField(node, "riderId"),
                    Map.of());
        } catch (Exception e) {
            log.error("Error processing driver.unavailable notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "ride.started", groupId = "notification-service")
    public void handleRideStarted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received ride.started event: rideId={}", node.get("rideId").asText());

            notificationRouter.notifyRider("ride.started",
                    getField(node, "riderId"),
                    Map.of());
        } catch (Exception e) {
            log.error("Error processing ride.started notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "ride.completed", groupId = "notification-service")
    public void handleRideCompleted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received ride.completed event: rideId={}", node.get("rideId").asText());

            Map<String, String> vars = new HashMap<>();
            vars.put("fare", getField(node, "fareActual"));

            notificationRouter.notifyRider("ride.completed",
                    getField(node, "riderId"),
                    vars);
        } catch (Exception e) {
            log.error("Error processing ride.completed notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "ride.cancelled", groupId = "notification-service")
    public void handleRideCancelled(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received ride.cancelled event: rideId={}", node.get("rideId").asText());

            String driverId = getField(node, "driverId");
            String riderId = getField(node, "riderId");

            notificationRouter.notifyRider("ride.cancelled", riderId, Map.of());
            if (driverId != null && !driverId.equals("null") && !driverId.isEmpty()) {
                notificationRouter.notifyDriver("ride.cancelled", driverId, Map.of());
            }
        } catch (Exception e) {
            log.error("Error processing ride.cancelled notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "payment.success", groupId = "notification-service")
    public void handlePaymentSuccess(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received payment.success event: rideId={}", node.get("rideId").asText());

            Map<String, String> vars = new HashMap<>();
            vars.put("amount", getField(node, "amount"));
            vars.put("txnId", getField(node, "gatewayTxnId"));

            notificationRouter.notifyRider("payment.success",
                    getField(node, "riderId"),
                    vars);
        } catch (Exception e) {
            log.error("Error processing payment.success notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "payment.failed", groupId = "notification-service")
    public void handlePaymentFailed(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received payment.failed event: rideId={}", node.get("rideId").asText());

            notificationRouter.notifyRider("payment.failed",
                    getField(node, "riderId"),
                    Map.of());
        } catch (Exception e) {
            log.error("Error processing payment.failed notification", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "payment.refunded", groupId = "notification-service")
    public void handlePaymentRefunded(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            log.info("Received payment.refunded event: rideId={}", node.get("rideId").asText());

            Map<String, String> vars = new HashMap<>();
            vars.put("amount", getField(node, "refundAmount"));
            vars.put("reason", getField(node, "reason"));

            notificationRouter.notifyRider("payment.refunded",
                    getField(node, "riderId"),
                    vars);
        } catch (Exception e) {
            log.error("Error processing payment.refunded notification", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = {
            "driver.assigned.DLT", "driver.unavailable.DLT",
            "ride.started.DLT", "ride.completed.DLT", "ride.cancelled.DLT",
            "payment.success.DLT", "payment.failed.DLT", "payment.refunded.DLT"
    }, groupId = "notification-service-dlt")
    public void handleDlt(String message) {
        log.error("Message sent to DLT: {}", message);
    }

    private String getField(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : "";
    }
}
