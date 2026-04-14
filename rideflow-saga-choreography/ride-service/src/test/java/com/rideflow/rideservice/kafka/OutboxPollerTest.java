package com.rideflow.rideservice.kafka;

import com.rideflow.rideservice.domain.OutboxEvent;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import com.rideflow.rideservice.service.OutboxPoller;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPoller outboxPoller;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, Object>> producerRecordCaptor;

    private OutboxEvent outboxEvent1;
    private OutboxEvent outboxEvent2;

    @BeforeEach
    void setUp() {
        outboxEvent1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(UUID.randomUUID().toString())
                .eventType("ride.requested")
                .payload("{\"rideId\":\"" + UUID.randomUUID() + "\"}")
                .published(false)
                .createdAt(LocalDateTime.now())
                .build();

        outboxEvent2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(UUID.randomUUID().toString())
                .eventType("ride.cancelled")
                .payload("{\"rideId\":\"" + UUID.randomUUID() + "\"}")
                .published(false)
                .createdAt(LocalDateTime.now().plusSeconds(1))
                .build();
    }

    @Test
    @DisplayName("pollAndPublish with unpublished events publishes to Kafka and marks published")
    @SuppressWarnings("unchecked")
    void pollAndPublish_unpublishedEvents_publishesToKafkaAndMarksPublished() {
        List<OutboxEvent> unpublishedEvents = List.of(outboxEvent1, outboxEvent2);
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(unpublishedEvents);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPoller.pollAndPublish();

        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        verify(outboxEventRepository, times(2)).save(outboxEventCaptor.capture());

        List<OutboxEvent> savedEvents = outboxEventCaptor.getAllValues();
        assertThat(savedEvents).hasSize(2);
        assertThat(savedEvents.get(0).isPublished()).isTrue();
        assertThat(savedEvents.get(1).isPublished()).isTrue();
    }

    @Test
    @DisplayName("pollAndPublish with unpublished events sends correct Kafka records")
    @SuppressWarnings("unchecked")
    void pollAndPublish_unpublishedEvents_sendsCorrectKafkaRecords() {
        List<OutboxEvent> unpublishedEvents = List.of(outboxEvent1);
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(unpublishedEvents);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPoller.pollAndPublish();

        verify(kafkaTemplate).send(producerRecordCaptor.capture());
        ProducerRecord<String, Object> capturedRecord = producerRecordCaptor.getValue();

        assertThat(capturedRecord.topic()).isEqualTo(outboxEvent1.getEventType());
        assertThat(capturedRecord.key()).isEqualTo(outboxEvent1.getAggregateId());
        assertThat(capturedRecord.value()).isEqualTo(outboxEvent1.getPayload());
    }

    @Test
    @DisplayName("pollAndPublish with no unpublished events does nothing")
    void pollAndPublish_noUnpublishedEvents_doesNothing() {
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(Collections.emptyList());

        outboxPoller.pollAndPublish();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
