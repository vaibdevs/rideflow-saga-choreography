package com.rideflow.paymentservice.service;

import com.rideflow.paymentservice.gateway.PaymentGateway;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeRequest;
import com.rideflow.paymentservice.gateway.PaymentGateway.ChargeResponse;
import com.rideflow.paymentservice.kafka.event.RideCompletedEvent;
import com.rideflow.paymentservice.kafka.producer.PaymentEventProducer;
import com.rideflow.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class PaymentIdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("rideflow_payments_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Disable Kafka for this integration test
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @MockBean
    private PaymentGateway paymentGateway;

    @MockBean
    private PaymentEventProducer eventProducer;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void sameRideCompletedEventDeliveredTwice_onlyChargedOnce() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("200.00");

        when(paymentGateway.charge(any(ChargeRequest.class)))
                .thenReturn(new ChargeResponse(true, "txn_" + UUID.randomUUID(), null));

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .driverId(UUID.randomUUID())
                .fareActual(fare)
                .timestamp(LocalDateTime.now())
                .build();

        // First call
        paymentService.processPayment(event);
        // Second call (duplicate delivery)
        paymentService.processPayment(event);

        // Gateway should only be charged once
        verify(paymentGateway, times(1)).charge(any(ChargeRequest.class));

        // Only one payment record in DB
        long count = paymentRepository.findAll().stream()
                .filter(p -> p.getRideId().equals(rideId))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void concurrentPaymentRequests_sameRideId_onlyOneSucceeds() throws InterruptedException {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("350.00");

        when(paymentGateway.charge(any(ChargeRequest.class)))
                .thenReturn(new ChargeResponse(true, "txn_" + UUID.randomUUID(), null));

        RideCompletedEvent event = RideCompletedEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .driverId(UUID.randomUUID())
                .fareActual(fare)
                .timestamp(LocalDateTime.now())
                .build();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // all threads start at same time
                    paymentService.processPayment(event);
                } catch (Exception e) {
                    // Expected: one thread may get DataIntegrityViolationException
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown(); // release all threads
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Only one payment should exist for this rideId
        long count = paymentRepository.findAll().stream()
                .filter(p -> p.getRideId().equals(rideId))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
