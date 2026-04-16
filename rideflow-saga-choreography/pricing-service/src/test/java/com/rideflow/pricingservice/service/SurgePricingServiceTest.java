package com.rideflow.pricingservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgePricingServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SurgePricingService surgePricingService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getSurgeMultiplier_ratioBelow2_returns1Point0() {
        // requests=5, drivers=10 -> ratio=0.5 -> 1.0
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("10");

        double multiplier = surgePricingService.getSurgeMultiplier(12.9716, 77.5946);

        assertThat(multiplier).isEqualTo(1.0);
    }

    @Test
    void getSurgeMultiplier_ratioAbove2_returns1Point5() {
        // requests=12, drivers=5 -> ratio=2.4 -> > 2.0 -> 1.5
        when(valueOperations.increment(anyString())).thenReturn(12L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("5");

        double multiplier = surgePricingService.getSurgeMultiplier(12.9716, 77.5946);

        assertThat(multiplier).isEqualTo(1.5);
    }

    @Test
    void getSurgeMultiplier_ratioAbove3_returns2Point0() {
        // requests=20, drivers=5 -> ratio=4.0 -> > 3.0 -> 2.0
        when(valueOperations.increment(anyString())).thenReturn(20L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("5");

        double multiplier = surgePricingService.getSurgeMultiplier(12.9716, 77.5946);

        assertThat(multiplier).isEqualTo(2.0);
    }

    @Test
    void getSurgeMultiplier_noDrivers_returnMaxMultiplier() {
        // drivers count is null -> defaults to 1, requests=20 -> ratio=20.0 -> > 3.0 -> 2.0
        // multiplier capped at min(2.0, 3.0) = 2.0
        when(valueOperations.increment(anyString())).thenReturn(20L);
        when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        double multiplier = surgePricingService.getSurgeMultiplier(12.9716, 77.5946);

        assertThat(multiplier).isEqualTo(2.0);
    }
}
