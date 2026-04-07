package com.rideflow.rideservice.exception;

public class InvalidRideTransitionException extends RuntimeException {

    public InvalidRideTransitionException(String message) {
        super(message);
    }
}
