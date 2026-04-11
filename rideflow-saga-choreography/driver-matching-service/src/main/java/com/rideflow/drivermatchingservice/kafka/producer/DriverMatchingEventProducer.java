package com.rideflow.drivermatchingservice.kafka.producer;

import com.rideflow.drivermatchingservice.kafka.event.DriverAssignedEvent;
import com.rideflow.drivermatchingservice.kafka.event.DriverUnavailableEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverMatchingEventProducer {

    private static final String DRIVER_ASSIGNED_TOPIC = "driver.assigned";
    private static final String DRIVER_UNAVAILABLE_TOPIC = "driver.unavailable";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishDriverAssigned(DriverAssignedEvent event) {
        log.info("Publishing driver.assigned event: rideId={}, driverId={}", event.getRideId(), event.getDriverId());

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                DRIVER_ASSIGNED_TOPIC,
                event.getRideId().toString(),
                event
        );
        addTraceIdHeader(record);

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish driver.assigned event: rideId={}", event.getRideId(), ex);
            } else {
                log.info("Successfully published driver.assigned event: rideId={}, offset={}",
                        event.getRideId(), result.getRecordMetadata().offset());
            }
        });
    }

    public void publishDriverUnavailable(DriverUnavailableEvent event) {
        log.info("Publishing driver.unavailable event: rideId={}, reason={}", event.getRideId(), event.getReason());

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                DRIVER_UNAVAILABLE_TOPIC,
                event.getRideId().toString(),
                event
        );
        addTraceIdHeader(record);

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish driver.unavailable event: rideId={}", event.getRideId(), ex);
            } else {
                log.info("Successfully published driver.unavailable event: rideId={}, offset={}",
                        event.getRideId(), result.getRecordMetadata().offset());
            }
        });
    }

    private void addTraceIdHeader(ProducerRecord<String, Object> record) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        record.headers().add(new RecordHeader("traceId", traceId.getBytes(StandardCharsets.UTF_8)));
    }
}
