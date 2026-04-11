package com.rideflow.drivermatchingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideOfferService {

    private static final String RIDE_OFFER_PREFIX = "ride:offer:";
    private static final String DRIVER_LOCK_PREFIX = "driver:lock:";
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(35);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void createOffer(UUID rideId, List<String> driverIds) {
        try {
            String key = RIDE_OFFER_PREFIX + rideId;
            String json = objectMapper.writeValueAsString(driverIds);
            stringRedisTemplate.opsForValue().set(key, json, OFFER_TTL);
            log.info("Created ride offer: rideId={}, drivers={}", rideId, driverIds);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize driver ids for ride offer: rideId={}", rideId, e);
            throw new RuntimeException("Failed to create ride offer", e);
        }
    }

    public List<String> getOfferedDrivers(UUID rideId) {
        try {
            String key = RIDE_OFFER_PREFIX + rideId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize driver ids for ride offer: rideId={}", rideId, e);
            return Collections.emptyList();
        }
    }

    public boolean isOfferActive(UUID rideId) {
        String key = RIDE_OFFER_PREFIX + rideId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public boolean acquireDriverLock(UUID driverId, UUID rideId) {
        String key = DRIVER_LOCK_PREFIX + driverId;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, rideId.toString(), LOCK_TTL);
        boolean result = Boolean.TRUE.equals(acquired);
        log.info("Driver lock acquisition: driverId={}, rideId={}, acquired={}", driverId, rideId, result);
        return result;
    }

    public void releaseDriverLock(UUID driverId) {
        String key = DRIVER_LOCK_PREFIX + driverId;
        stringRedisTemplate.delete(key);
        log.info("Released driver lock: driverId={}", driverId);
    }
}
