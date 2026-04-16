package com.rideflow.pricingservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FareCalculatorTest {

    private FareCalculator fareCalculator;

    @BeforeEach
    void setUp() {
        fareCalculator = new FareCalculator();
    }

    @Test
    void calculate_noSurge_returnsBaseFarePlusDistancePlusTime() {
        // fare = (30 + 10*12 + 20*1.5) * 1.0 = (30 + 120 + 30) * 1.0 = 180
        BigDecimal result = fareCalculator.calculate(10.0, 20.0, 1.0);

        assertThat(result).isEqualByComparingTo(new BigDecimal("180"));
    }

    @Test
    void calculate_surgeMultiplier1Point5_returnsSurgedFare() {
        // fare = (30 + 10*12 + 20*1.5) * 1.5 = 180 * 1.5 = 270
        BigDecimal result = fareCalculator.calculate(10.0, 20.0, 1.5);

        assertThat(result).isEqualByComparingTo(new BigDecimal("270"));
    }

    @Test
    void calculate_alwaysRoundsUpToNearestRupee() {
        // fare = (30 + 5*12 + 7*1.5) * 1.0 = (30 + 60 + 10.5) * 1.0 = 100.5 -> CEILING -> 101
        BigDecimal result = fareCalculator.calculate(5.0, 7.0, 1.0);

        assertThat(result).isEqualByComparingTo(new BigDecimal("101"));
    }

    @Test
    void calculate_zeroDistance_returnsBaseFareOnly() {
        // fare = (30 + 0*12 + 0*1.5) * 1.0 = 30
        BigDecimal result = fareCalculator.calculate(0.0, 0.0, 1.0);

        assertThat(result).isEqualByComparingTo(new BigDecimal("30"));
    }
}
