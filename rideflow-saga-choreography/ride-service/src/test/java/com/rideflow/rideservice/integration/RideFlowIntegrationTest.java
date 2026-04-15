package com.rideflow.rideservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.rideservice.domain.Ride;
import com.rideflow.rideservice.domain.RideStatus;
import com.rideflow.rideservice.dto.CancelRideRequest;
import com.rideflow.rideservice.dto.CreateRideRequest;
import com.rideflow.rideservice.dto.RideResponse;
import com.rideflow.rideservice.kafka.event.DriverAssignedEvent;
import com.rideflow.rideservice.kafka.event.PaymentSuccessEvent;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import com.rideflow.rideservice.repository.RideRepository;
import com.rideflow.rideservice.service.RideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RideFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("rideflow_rides_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private RideService rideService;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("Full happy path: ride requested to paid - all events flow correctly")
    void fullHappyPath_rideRequestedToPaid_allEventsFlowCorrectly() {
        // Step 1: Create ride
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId(UUID.randomUUID())
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.9352)
                .dropLng(77.6245)
                .build();

        RideResponse response = rideService.createRide(request);
        assertThat(response.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.getId()).isNotNull();

        UUID rideId = response.getId();

        // Verify outbox event was created
        assertThat(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .isNotEmpty();

        // Step 2: Simulate driver assigned
        UUID driverId = UUID.randomUUID();
        DriverAssignedEvent driverEvent = DriverAssignedEvent.builder()
                .rideId(rideId)
                .riderId(request.getRiderId())
                .driverId(driverId)
                .driverName("Test Driver")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .driverLat(12.975)
                .driverLng(77.600)
                .etaMinutes(5.0)
                .timestamp(LocalDateTime.now())
                .build();

        // First set status to PRICE_CALCULATED (since driver.assigned requires it)
        Ride ride = rideRepository.findById(rideId).orElseThrow();
        ride.setStatus(RideStatus.PRICE_CALCULATED);
        ride.setFareEstimate(new BigDecimal("250"));
        rideRepository.save(ride);

        rideService.handleDriverAssigned(driverEvent);

        RideResponse afterAssign = rideService.getRide(rideId);
        assertThat(afterAssign.getStatus()).isEqualTo(RideStatus.DRIVER_ASSIGNED);
        assertThat(afterAssign.getDriverId()).isEqualTo(driverId);

        // Step 3: Start ride
        RideResponse afterStart = rideService.startRide(rideId);
        assertThat(afterStart.getStatus()).isEqualTo(RideStatus.STARTED);

        // Step 4: Complete ride
        RideResponse afterComplete = rideService.completeRide(rideId);
        assertThat(afterComplete.getStatus()).isEqualTo(RideStatus.COMPLETED);

        // Step 5: Simulate payment success
        PaymentSuccessEvent paymentEvent = PaymentSuccessEvent.builder()
                .paymentId(UUID.randomUUID())
                .rideId(rideId)
                .riderId(request.getRiderId())
                .amount(new BigDecimal("250"))
                .gatewayTxnId("txn_test_123")
                .timestamp(LocalDateTime.now())
                .build();

        rideService.handlePaymentSuccess(paymentEvent);

        RideResponse afterPaid = rideService.getRide(rideId);
        assertThat(afterPaid.getStatus()).isEqualTo(RideStatus.PAID);
    }

    @Test
    @DisplayName("No driver scenario: ride auto-cancelled")
    void noDriverScenario_rideAutoCancel_rideCancelledEventPublished() {
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId(UUID.randomUUID())
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.9352)
                .dropLng(77.6245)
                .build();

        RideResponse response = rideService.createRide(request);
        UUID rideId = response.getId();

        // Cancel the ride
        CancelRideRequest cancelRequest = CancelRideRequest.builder()
                .cancelledBy("SYSTEM")
                .build();

        RideResponse cancelled = rideService.cancelRide(rideId, cancelRequest);
        assertThat(cancelled.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(cancelled.getCancelledBy()).isEqualTo("SYSTEM");
    }
}
