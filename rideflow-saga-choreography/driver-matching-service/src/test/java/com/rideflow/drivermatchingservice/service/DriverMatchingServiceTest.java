package com.rideflow.drivermatchingservice.service;

import com.rideflow.drivermatchingservice.domain.Driver;
import com.rideflow.drivermatchingservice.domain.DriverStatus;
import com.rideflow.drivermatchingservice.kafka.event.DriverAssignedEvent;
import com.rideflow.drivermatchingservice.kafka.event.DriverUnavailableEvent;
import com.rideflow.drivermatchingservice.kafka.event.PriceCalculatedEvent;
import com.rideflow.drivermatchingservice.kafka.producer.DriverMatchingEventProducer;
import com.rideflow.drivermatchingservice.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverMatchingServiceTest {

    @Mock
    private GeoSearchService geoSearchService;

    @Mock
    private RideOfferService rideOfferService;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverMatchingEventProducer eventProducer;

    @InjectMocks
    private DriverMatchingService driverMatchingService;

    private PriceCalculatedEvent priceCalculatedEvent;
    private UUID rideId;
    private UUID riderId;

    @BeforeEach
    void setUp() {
        rideId = UUID.randomUUID();
        riderId = UUID.randomUUID();
        priceCalculatedEvent = PriceCalculatedEvent.builder()
                .rideId(rideId)
                .riderId(riderId)
                .pickupLat(12.97)
                .pickupLng(77.59)
                .dropLat(13.01)
                .dropLng(77.65)
                .fareEstimate(BigDecimal.valueOf(250.0))
                .surgeMultiplier(1.0)
                .distanceKm(8.5)
                .etaMinutes(15.0)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("processRideForMatching - drivers available - creates offer for top 3 drivers")
    void findAndOfferDrivers_driversAvailable_sendsOfferToTop3() {
        List<String> nearbyDrivers = List.of("d1", "d2", "d3");
        when(geoSearchService.findNearestDrivers(12.97, 77.59, 5.0, 3))
                .thenReturn(nearbyDrivers);

        driverMatchingService.processRideForMatching(priceCalculatedEvent);

        verify(rideOfferService).createOffer(rideId, nearbyDrivers);
        verify(eventProducer, never()).publishDriverUnavailable(any(DriverUnavailableEvent.class));
    }

    @Test
    @DisplayName("processRideForMatching - no drivers available - publishes DriverUnavailableEvent")
    void findAndOfferDrivers_noDrivers_publishesDriverUnavailableEvent() {
        when(geoSearchService.findNearestDrivers(12.97, 77.59, 5.0, 3))
                .thenReturn(Collections.emptyList());

        driverMatchingService.processRideForMatching(priceCalculatedEvent);

        verify(eventProducer).publishDriverUnavailable(argThat(event ->
                event.getRideId().equals(rideId) &&
                event.getRiderId().equals(riderId) &&
                event.getReason().contains("No drivers available")
        ));
        verify(rideOfferService, never()).createOffer(any(), any());
    }

    @Test
    @DisplayName("handleDriverAcceptance - lock acquired - publishes DriverAssignedEvent")
    void handleAcceptance_lockAcquired_publishesDriverAssigned() {
        UUID driverId = UUID.randomUUID();
        Driver driver = Driver.builder()
                .id(driverId)
                .name("Test Driver")
                .phoneNumber("9876543210")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .currentLat(12.97)
                .currentLng(77.59)
                .status(DriverStatus.ONLINE)
                .build();

        when(rideOfferService.isOfferActive(rideId)).thenReturn(true);
        when(rideOfferService.acquireDriverLock(driverId, rideId)).thenReturn(true);
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        driverMatchingService.handleDriverAcceptance(driverId, rideId);

        verify(eventProducer).publishDriverAssigned(argThat(event ->
                event.getRideId().equals(rideId) &&
                event.getDriverId().equals(driverId) &&
                event.getDriverName().equals("Test Driver") &&
                event.getVehicleNumber().equals("KA01AB1234") &&
                event.getVehicleType().equals("SEDAN")
        ));
        verify(driverRepository).save(argThat(d -> d.getStatus() == DriverStatus.ON_TRIP));
        verify(geoSearchService).removeDriverLocation(driverId.toString());
    }

    @Test
    @DisplayName("handleDriverAcceptance - lock already held - does not publish DriverAssignedEvent")
    void handleAcceptance_lockAlreadyHeld_doesNotPublish() {
        UUID driverId = UUID.randomUUID();

        when(rideOfferService.isOfferActive(rideId)).thenReturn(true);
        when(rideOfferService.acquireDriverLock(driverId, rideId)).thenReturn(false);

        driverMatchingService.handleDriverAcceptance(driverId, rideId);

        verify(eventProducer, never()).publishDriverAssigned(any(DriverAssignedEvent.class));
        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("handleDriverAcceptance - offer no longer active - does not attempt lock")
    void handleAcceptance_offerExpired_doesNotAttemptLock() {
        UUID driverId = UUID.randomUUID();

        when(rideOfferService.isOfferActive(rideId)).thenReturn(false);

        driverMatchingService.handleDriverAcceptance(driverId, rideId);

        verify(rideOfferService, never()).acquireDriverLock(any(), any());
        verify(eventProducer, never()).publishDriverAssigned(any(DriverAssignedEvent.class));
    }

    @Test
    @DisplayName("handleDriverRejection - releases driver lock")
    void handleDriverRejection_releasesDriverLock() {
        UUID driverId = UUID.randomUUID();

        driverMatchingService.handleDriverRejection(driverId, rideId);

        verify(rideOfferService).releaseDriverLock(driverId);
    }
}
