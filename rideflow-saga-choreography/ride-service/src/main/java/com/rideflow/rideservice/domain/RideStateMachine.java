package com.rideflow.rideservice.domain;

import com.rideflow.rideservice.exception.InvalidRideTransitionException;

import java.util.List;
import java.util.Map;

public class RideStateMachine {

    private static final Map<RideStatus, List<RideStatus>> ALLOWED_TRANSITIONS = Map.of(
            RideStatus.REQUESTED, List.of(RideStatus.PRICE_CALCULATED, RideStatus.CANCELLED),
            RideStatus.PRICE_CALCULATED, List.of(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ASSIGNED, List.of(RideStatus.STARTED, RideStatus.CANCELLED),
            RideStatus.STARTED, List.of(RideStatus.COMPLETED, RideStatus.CANCELLED),
            RideStatus.COMPLETED, List.of(RideStatus.PAID, RideStatus.PAYMENT_FAILED, RideStatus.CANCELLED),
            RideStatus.PAID, List.of(),
            RideStatus.CANCELLED, List.of(),
            RideStatus.PAYMENT_FAILED, List.of(RideStatus.CANCELLED)
    );

    private RideStateMachine() {
    }

    public static boolean isValidTransition(RideStatus from, RideStatus to) {
        List<RideStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void validateTransition(RideStatus from, RideStatus to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidRideTransitionException(
                    String.format("Invalid ride status transition from %s to %s", from, to));
        }
    }
}
