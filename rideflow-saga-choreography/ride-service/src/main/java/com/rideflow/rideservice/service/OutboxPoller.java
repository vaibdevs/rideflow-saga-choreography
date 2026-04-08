package com.rideflow.rideservice.service;

import com.rideflow.rideservice.domain.OutboxEvent;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();

        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Found {} unpublished outbox events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getEventType(),
                        event.getAggregateId(),
                        event.getPayload()
                );

                String traceId = MDC.get("traceId");
                if (traceId != null) {
                    record.headers().add(new RecordHeader("traceId",
                            traceId.getBytes(StandardCharsets.UTF_8)));
                }

                kafkaTemplate.send(record).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish outbox event: id={}, type={}", event.getId(), event.getEventType(), ex);
                    }
                });

                event.setPublished(true);
                outboxEventRepository.save(event);
                log.info("Published outbox event: id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Error publishing outbox event: id={}, type={}", event.getId(), event.getEventType(), e);
            }
        }
    }
}
