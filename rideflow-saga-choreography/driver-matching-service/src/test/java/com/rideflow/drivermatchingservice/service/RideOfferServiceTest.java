package com.rideflow.drivermatchingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideOfferServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RideOfferService rideOfferService;

    private UUID rideId;

    @BeforeEach
    void setUp() {
        rideId = UUID.randomUUID();
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("createOffer - valid ride and drivers - stored in Redis with TTL")
    void createOffer_validRideAndDrivers_storedInRedisWithTTL() throws JsonProcessingException {
        List<String> driverIds = List.of("d1", "d2", "d3");
        String json = "[\"d1\",\"d2\",\"d3\"]";
        when(objectMapper.writeValueAsString(driverIds)).thenReturn(json);

        rideOfferService.createOffer(rideId, driverIds);

        verify(valueOperations).set(
                eq("ride:offer:" + rideId),
                eq(json),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    @DisplayName("createOffer - serialization failure - throws RuntimeException")
    void createOffer_serializationFailure_throwsRuntimeException() throws JsonProcessingException {
        List<String> driverIds = List.of("d1", "d2");
        when(objectMapper.writeValueAsString(driverIds)).thenThrow(new JsonProcessingException("error") {});

        assertThatThrownBy(() -> rideOfferService.createOffer(rideId, driverIds))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create ride offer");
    }

    @Test
    @DisplayName("getOfferedDrivers - offer exists - returns driver list")
    void getOfferedDrivers_offerExists_returnsDriverList() throws JsonProcessingException {
        String json = "[\"d1\",\"d2\",\"d3\"]";
        when(valueOperations.get("ride:offer:" + rideId)).thenReturn(json);
        when(objectMapper.readValue(eq(json), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of("d1", "d2", "d3"));

        List<String> drivers = rideOfferService.getOfferedDrivers(rideId);

        assertThat(drivers).containsExactly("d1", "d2", "d3");
    }

    @Test
    @DisplayName("getOfferedDrivers - offer expired (null) - returns empty list")
    void getOfferedDrivers_offerExpired_returnsEmptyList() {
        when(valueOperations.get("ride:offer:" + rideId)).thenReturn(null);

        List<String> drivers = rideOfferService.getOfferedDrivers(rideId);

        assertThat(drivers).isEmpty();
    }

    @Test
    @DisplayName("isOfferActive - offer exists - returns true")
    void isOfferActive_offerExists_returnsTrue() {
        when(stringRedisTemplate.hasKey("ride:offer:" + rideId)).thenReturn(true);

        assertThat(rideOfferService.isOfferActive(rideId)).isTrue();
    }

    @Test
    @DisplayName("isOfferActive - expired TTL - returns false")
    void isOfferActive_expiredTTL_returnsFalse() {
        when(stringRedisTemplate.hasKey("ride:offer:" + rideId)).thenReturn(false);

        assertThat(rideOfferService.isOfferActive(rideId)).isFalse();
    }

    @Test
    @DisplayName("acquireDriverLock - lock available - returns true")
    void acquireDriverLock_lockAvailable_returnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(valueOperations.setIfAbsent(
                eq("driver:lock:" + driverId),
                eq(rideId.toString()),
                eq(Duration.ofSeconds(35))
        )).thenReturn(true);

        assertThat(rideOfferService.acquireDriverLock(driverId, rideId)).isTrue();
    }

    @Test
    @DisplayName("acquireDriverLock - lock already held - returns false")
    void acquireDriverLock_lockAlreadyHeld_returnsFalse() {
        UUID driverId = UUID.randomUUID();
        when(valueOperations.setIfAbsent(
                eq("driver:lock:" + driverId),
                eq(rideId.toString()),
                eq(Duration.ofSeconds(35))
        )).thenReturn(false);

        assertThat(rideOfferService.acquireDriverLock(driverId, rideId)).isFalse();
    }

    @Test
    @DisplayName("releaseDriverLock - releases lock from Redis")
    void releaseDriverLock_releasesLockFromRedis() {
        UUID driverId = UUID.randomUUID();

        rideOfferService.releaseDriverLock(driverId);

        verify(stringRedisTemplate).delete("driver:lock:" + driverId);
    }
}
