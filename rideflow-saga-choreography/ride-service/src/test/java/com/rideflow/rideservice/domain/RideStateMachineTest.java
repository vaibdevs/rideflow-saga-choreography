package com.rideflow.rideservice.domain;

import com.rideflow.rideservice.exception.InvalidRideTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideStateMachineTest {

    @Test
    @DisplayName("isValidTransition from REQUESTED to PRICE_CALCULATED returns true")
    void isValidTransition_requestedToPriceCalculated_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.REQUESTED, RideStatus.PRICE_CALCULATED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from REQUESTED to COMPLETED returns false")
    void isValidTransition_requestedToCompleted_returnsFalse() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.REQUESTED, RideStatus.COMPLETED))
                .isFalse();
    }

    @Test
    @DisplayName("isValidTransition from REQUESTED to CANCELLED returns true")
    void isValidTransition_requestedToCancelled_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.REQUESTED, RideStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from PRICE_CALCULATED to CANCELLED returns true")
    void isValidTransition_priceCalculatedToCancelled_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.PRICE_CALCULATED, RideStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from DRIVER_ASSIGNED to CANCELLED returns true")
    void isValidTransition_driverAssignedToCancelled_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from STARTED to CANCELLED returns true")
    void isValidTransition_startedToCancelled_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.STARTED, RideStatus.CANCELLED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from PAID to CANCELLED returns false")
    void isValidTransition_paidToCancelled_returnsFalse() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.PAID, RideStatus.CANCELLED))
                .isFalse();
    }

    @Test
    @DisplayName("validateTransition with invalid transition throws InvalidRideTransitionException")
    void validateTransition_invalidTransition_throwsException() {
        assertThatThrownBy(() -> RideStateMachine.validateTransition(RideStatus.REQUESTED, RideStatus.COMPLETED))
                .isInstanceOf(InvalidRideTransitionException.class)
                .hasMessageContaining("Invalid ride status transition from REQUESTED to COMPLETED");
    }

    @Test
    @DisplayName("validateTransition with valid transition does not throw")
    void validateTransition_validTransition_doesNotThrow() {
        RideStateMachine.validateTransition(RideStatus.REQUESTED, RideStatus.PRICE_CALCULATED);
        // No exception means the test passes
    }

    @Test
    @DisplayName("isValidTransition from COMPLETED to PAID returns true")
    void isValidTransition_completedToPaid_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.COMPLETED, RideStatus.PAID))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from COMPLETED to PAYMENT_FAILED returns true")
    void isValidTransition_completedToPaymentFailed_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.COMPLETED, RideStatus.PAYMENT_FAILED))
                .isTrue();
    }

    @Test
    @DisplayName("isValidTransition from CANCELLED to any state returns false")
    void isValidTransition_cancelledToAnyState_returnsFalse() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.CANCELLED, RideStatus.REQUESTED)).isFalse();
        assertThat(RideStateMachine.isValidTransition(RideStatus.CANCELLED, RideStatus.STARTED)).isFalse();
        assertThat(RideStateMachine.isValidTransition(RideStatus.CANCELLED, RideStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("isValidTransition from PAYMENT_FAILED to CANCELLED returns true")
    void isValidTransition_paymentFailedToCancelled_returnsTrue() {
        assertThat(RideStateMachine.isValidTransition(RideStatus.PAYMENT_FAILED, RideStatus.CANCELLED))
                .isTrue();
    }
}
