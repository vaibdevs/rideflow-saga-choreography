package com.rideflow.rideservice.controller;

import com.rideflow.rideservice.dto.CancelRideRequest;
import com.rideflow.rideservice.dto.CreateRideRequest;
import com.rideflow.rideservice.dto.RideResponse;
import com.rideflow.rideservice.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        log.info("Creating ride for rider: {}", request.getRiderId());
        RideResponse response = rideService.createRide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideResponse> getRide(@PathVariable UUID id) {
        return ResponseEntity.ok(rideService.getRide(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<RideResponse> cancelRide(@PathVariable UUID id,
                                                    @Valid @RequestBody CancelRideRequest request) {
        log.info("Cancelling ride: rideId={}, cancelledBy={}", id, request.getCancelledBy());
        return ResponseEntity.ok(rideService.cancelRide(id, request));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<RideResponse> startRide(@PathVariable UUID id) {
        log.info("Starting ride: rideId={}", id);
        return ResponseEntity.ok(rideService.startRide(id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<RideResponse> completeRide(@PathVariable UUID id) {
        log.info("Completing ride: rideId={}", id);
        return ResponseEntity.ok(rideService.completeRide(id));
    }
}
