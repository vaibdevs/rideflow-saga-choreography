package com.rideflow.drivermatchingservice.service;

import com.rideflow.drivermatchingservice.domain.Driver;
import com.rideflow.drivermatchingservice.domain.DriverStatus;
import com.rideflow.drivermatchingservice.exception.DriverNotFoundException;
import com.rideflow.drivermatchingservice.kafka.event.*;
import com.rideflow.drivermatchingservice.kafka.producer.DriverMatchingEventProducer;
import com.rideflow.drivermatchingservice.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverMatchingService {

    private static final double SEARCH_RADIUS_KM = 5.0;
    private static final int MAX_DRIVERS = 3;

    private final GeoSearchService geoSearchService;
    private final RideOfferService rideOfferService;
    private final DriverRepository driverRepository;
    private final DriverMatchingEventProducer eventProducer;

    public void processRideForMatching(PriceCalculatedEvent event) {
        log.info("Processing ride for matching: rideId={}, pickupLat={}, pickupLng={}",
                event.getRideId(), event.getPickupLat(), event.getPickupLng());

        List<String> nearbyDriverIds = geoSearchService.findNearestDrivers(
                event.getPickupLat(), event.getPickupLng(), SEARCH_RADIUS_KM, MAX_DRIVERS);

        if (nearbyDriverIds.isEmpty()) {
            log.warn("No drivers available for ride: rideId={}", event.getRideId());
            eventProducer.publishDriverUnavailable(DriverUnavailableEvent.builder()
                    .rideId(event.getRideId())
                    .riderId(event.getRiderId())
                    .reason("No drivers available within " + SEARCH_RADIUS_KM + " km radius")
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        log.info("Found {} nearby drivers for ride: rideId={}, driverIds={}",
                nearbyDriverIds.size(), event.getRideId(), nearbyDriverIds);

        rideOfferService.createOffer(event.getRideId(), nearbyDriverIds);

        log.info("Ride offer created successfully: rideId={}, offeredTo={}", event.getRideId(), nearbyDriverIds);
    }

    @Transactional
    public void handleDriverAcceptance(UUID driverId, UUID rideId) {
        log.info("Handling driver acceptance: driverId={}, rideId={}", driverId, rideId);

        if (!rideOfferService.isOfferActive(rideId)) {
            log.warn("Ride offer is no longer active: rideId={}", rideId);
            return;
        }

        boolean lockAcquired = rideOfferService.acquireDriverLock(driverId, rideId);
        if (!lockAcquired) {
            log.warn("Failed to acquire driver lock - driver may already be assigned: driverId={}, rideId={}",
                    driverId, rideId);
            return;
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));

        driver.setStatus(DriverStatus.ON_TRIP);
        driverRepository.save(driver);

        geoSearchService.removeDriverLocation(driverId.toString());

        log.info("Driver assigned to ride: driverId={}, rideId={}", driverId, rideId);

        eventProducer.publishDriverAssigned(DriverAssignedEvent.builder()
                .rideId(rideId)
                .riderId(null)
                .driverId(driverId)
                .driverName(driver.getName())
                .vehicleNumber(driver.getVehicleNumber())
                .vehicleType(driver.getVehicleType())
                .driverLat(driver.getCurrentLat())
                .driverLng(driver.getCurrentLng())
                .etaMinutes(null)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void handleDriverRejection(UUID driverId, UUID rideId) {
        log.info("Handling driver rejection: driverId={}, rideId={}", driverId, rideId);
        rideOfferService.releaseDriverLock(driverId);
    }

    @Transactional
    public void handleRideCancellation(RideCancelledEvent event) {
        log.info("Handling ride cancellation: rideId={}, driverId={}, cancelledBy={}",
                event.getRideId(), event.getDriverId(), event.getCancelledBy());

        if (event.getDriverId() != null) {
            rideOfferService.releaseDriverLock(event.getDriverId());

            driverRepository.findById(event.getDriverId()).ifPresent(driver -> {
                driver.setStatus(DriverStatus.ONLINE);
                driverRepository.save(driver);

                if (driver.getCurrentLat() != null && driver.getCurrentLng() != null) {
                    geoSearchService.addDriverLocation(
                            driver.getId().toString(), driver.getCurrentLat(), driver.getCurrentLng());
                }

                log.info("Driver set back to ONLINE after ride cancellation: driverId={}", event.getDriverId());
            });
        }
    }
}
