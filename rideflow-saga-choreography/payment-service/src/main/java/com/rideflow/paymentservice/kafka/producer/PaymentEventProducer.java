package com.rideflow.paymentservice.kafka.producer;

import com.rideflow.paymentservice.kafka.event.PaymentFailedEvent;
import com.rideflow.paymentservice.kafka.event.PaymentRefundedEvent;
import com.rideflow.paymentservice.kafka.event.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "payment.success", event.getRideId().toString(), event);
        addTraceHeader(record);
        kafkaTemplate.send(record);
        log.info("Published payment.success event: rideId={}, paymentId={}", event.getRideId(), event.getPaymentId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "payment.failed", event.getRideId().toString(), event);
        addTraceHeader(record);
        kafkaTemplate.send(record);
        log.info("Published payment.failed event: rideId={}", event.getRideId());
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "payment.refunded", event.getRideId().toString(), event);
        addTraceHeader(record);
        kafkaTemplate.send(record);
        log.info("Published payment.refunded event: rideId={}", event.getRideId());
    }

    private void addTraceHeader(ProducerRecord<String, Object> record) {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            record.headers().add(new RecordHeader("traceId", traceId.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
