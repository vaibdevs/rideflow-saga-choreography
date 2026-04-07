package com.rideflow.rideservice.exception;

import java.util.UUID;

public class RideNotFoundException extends RuntimeException {

    public RideNotFoundException(UUID rideId) {
        super("Ride not found with id: " + rideId);
    }
}
