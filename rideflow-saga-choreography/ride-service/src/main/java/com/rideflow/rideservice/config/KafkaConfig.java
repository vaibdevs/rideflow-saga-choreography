package com.rideflow.rideservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public NewTopic rideRequestedTopic() {
        return TopicBuilder.name("ride.requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideStartedTopic() {
        return TopicBuilder.name("ride.started").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCompletedTopic() {
        return TopicBuilder.name("ride.completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCancelledTopic() {
        return TopicBuilder.name("ride.cancelled").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideRequestedDlt() {
        return TopicBuilder.name("ride.requested.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic rideStartedDlt() {
        return TopicBuilder.name("ride.started.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic rideCompletedDlt() {
        return TopicBuilder.name("ride.completed.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic rideCancelledDlt() {
        return TopicBuilder.name("ride.cancelled.DLT").partitions(1).replicas(1).build();
    }
}
