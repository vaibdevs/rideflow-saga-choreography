package com.rideflow.rideservice.domain;

public enum RideStatus {
    REQUESTED,
    PRICE_CALCULATED,
    DRIVER_ASSIGNED,
    STARTED,
    COMPLETED,
    PAID,
    CANCELLED,
    PAYMENT_FAILED
}
