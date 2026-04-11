package com.rideflow.drivermatchingservice.controller;

import com.rideflow.drivermatchingservice.domain.Driver;
import com.rideflow.drivermatchingservice.domain.DriverStatus;
import com.rideflow.drivermatchingservice.dto.DriverResponse;
import com.rideflow.drivermatchingservice.dto.GoOnlineRequest;
import com.rideflow.drivermatchingservice.dto.RegisterDriverRequest;
import com.rideflow.drivermatchingservice.exception.DriverNotFoundException;
import com.rideflow.drivermatchingservice.repository.DriverRepository;
import com.rideflow.drivermatchingservice.service.DriverMatchingService;
import com.rideflow.drivermatchingservice.service.GeoSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverRepository driverRepository;
    private final GeoSearchService geoSearchService;
    private final DriverMatchingService driverMatchingService;

    @PostMapping
    public ResponseEntity<DriverResponse> registerDriver(@Valid @RequestBody RegisterDriverRequest request) {
        log.info("Registering new driver: name={}, vehicleNumber={}", request.getName(), request.getVehicleNumber());

        Driver driver = Driver.builder()
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .vehicleNumber(request.getVehicleNumber())
                .vehicleType(request.getVehicleType())
                .build();

        Driver saved = driverRepository.save(driver);
        log.info("Driver registered successfully: id={}", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/{id}/online")
    public ResponseEntity<DriverResponse> goOnline(@PathVariable UUID id,
                                                    @Valid @RequestBody GoOnlineRequest request) {
        log.info("Driver going online: id={}, lat={}, lng={}", id, request.getLat(), request.getLng());

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException(id));

        driver.setStatus(DriverStatus.ONLINE);
        driver.setCurrentLat(request.getLat());
        driver.setCurrentLng(request.getLng());
        Driver saved = driverRepository.save(driver);

        geoSearchService.addDriverLocation(id.toString(), request.getLat(), request.getLng());

        log.info("Driver is now online: id={}", id);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/{id}/offline")
    public ResponseEntity<DriverResponse> goOffline(@PathVariable UUID id) {
        log.info("Driver going offline: id={}", id);

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException(id));

        driver.setStatus(DriverStatus.OFFLINE);
        Driver saved = driverRepository.save(driver);

        geoSearchService.removeDriverLocation(id.toString());

        log.info("Driver is now offline: id={}", id);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/{id}/accept/{rideId}")
    public ResponseEntity<Void> acceptRide(@PathVariable UUID id, @PathVariable UUID rideId) {
        log.info("Driver accepting ride: driverId={}, rideId={}", id, rideId);
        driverMatchingService.handleDriverAcceptance(id, rideId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject/{rideId}")
    public ResponseEntity<Void> rejectRide(@PathVariable UUID id, @PathVariable UUID rideId) {
        log.info("Driver rejecting ride: driverId={}, rideId={}", id, rideId);
        driverMatchingService.handleDriverRejection(id, rideId);
        return ResponseEntity.ok().build();
    }

    private DriverResponse toResponse(Driver driver) {
        return DriverResponse.builder()
                .id(driver.getId())
                .name(driver.getName())
                .phoneNumber(driver.getPhoneNumber())
                .vehicleNumber(driver.getVehicleNumber())
                .vehicleType(driver.getVehicleType())
                .rating(driver.getRating())
                .status(driver.getStatus())
                .currentLat(driver.getCurrentLat())
                .currentLng(driver.getCurrentLng())
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }
}
