package com.rideflow.rideservice.service;

import com.rideflow.rideservice.domain.OutboxEvent;
import com.rideflow.rideservice.domain.Ride;
import com.rideflow.rideservice.domain.RideStatus;
import com.rideflow.rideservice.dto.CancelRideRequest;
import com.rideflow.rideservice.dto.CreateRideRequest;
import com.rideflow.rideservice.dto.RideResponse;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import com.rideflow.rideservice.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RideServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("rideflow_rides_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("management.tracing.enabled", () -> "false");
    }

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RideService rideService;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        rideRepository.deleteAll();
    }

    @Test
    @DisplayName("createRide full flow - ride and outbox event persisted")
    void createRide_fullFlow_rideAndOutboxEventPersisted() {
        UUID riderId = UUID.randomUUID();
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId(riderId)
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.2958)
                .dropLng(76.6394)
                .build();

        RideResponse response = rideService.createRide(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.getRiderId()).isEqualTo(riderId);
        assertThat(response.getPickupLat()).isEqualTo(12.9716);
        assertThat(response.getPickupLng()).isEqualTo(77.5946);
        assertThat(response.getDropLat()).isEqualTo(12.2958);
        assertThat(response.getDropLng()).isEqualTo(76.6394);

        // Verify ride persisted in DB
        Ride savedRide = rideRepository.findById(response.getId()).orElse(null);
        assertThat(savedRide).isNotNull();
        assertThat(savedRide.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(savedRide.getRiderId()).isEqualTo(riderId);
        assertThat(savedRide.getCreatedAt()).isNotNull();

        // Verify outbox event persisted
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ride.requested");
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo(response.getId().toString());
        assertThat(outboxEvents.get(0).isPublished()).isFalse();
        assertThat(outboxEvents.get(0).getPayload()).contains(response.getId().toString());
    }

    @Test
    @DisplayName("cancelRide after DRIVER_ASSIGNED - status changed to CANCELLED")
    void cancelRide_afterDriverAssigned_statusChangedToCancelled() {
        // Create ride first
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId(riderId)
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.2958)
                .dropLng(76.6394)
                .build();

        RideResponse createResponse = rideService.createRide(request);
        UUID rideId = createResponse.getId();

        // Manually set ride to DRIVER_ASSIGNED (simulating the saga flow)
        Ride ride = rideRepository.findById(rideId).orElseThrow();
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(driverId);
        rideRepository.save(ride);

        // Cancel the ride
        CancelRideRequest cancelRequest = CancelRideRequest.builder()
                .cancelledBy("RIDER")
                .build();

        RideResponse cancelResponse = rideService.cancelRide(rideId, cancelRequest);

        assertThat(cancelResponse.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(cancelResponse.getCancelledBy()).isEqualTo("RIDER");

        // Verify in DB
        Ride cancelledRide = rideRepository.findById(rideId).orElseThrow();
        assertThat(cancelledRide.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(cancelledRide.getCancelledBy()).isEqualTo("RIDER");

        // Verify outbox has both ride.requested and ride.cancelled events
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxEvents).hasSizeGreaterThanOrEqualTo(2);

        boolean hasRequestedEvent = outboxEvents.stream()
                .anyMatch(e -> "ride.requested".equals(e.getEventType()));
        boolean hasCancelledEvent = outboxEvents.stream()
                .anyMatch(e -> "ride.cancelled".equals(e.getEventType()));

        assertThat(hasRequestedEvent).isTrue();
        assertThat(hasCancelledEvent).isTrue();
    }
}
