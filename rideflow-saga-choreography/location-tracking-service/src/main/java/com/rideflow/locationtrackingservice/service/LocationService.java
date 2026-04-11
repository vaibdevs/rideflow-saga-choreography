package com.rideflow.locationtrackingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final double AVERAGE_SPEED_KMH = 30.0;

    public void updateDriverLocation(String driverId, double lat, double lng, String rideId) {
        String key = "driver:location:" + driverId;

        Map<String, String> locationData = new HashMap<>();
        locationData.put("lat", String.valueOf(lat));
        locationData.put("lng", String.valueOf(lng));
        locationData.put("updatedAt", Instant.now().toString());

        redisTemplate.opsForHash().putAll(key, locationData);
        redisTemplate.expire(key, 10, TimeUnit.MINUTES);

        log.debug("Updated driver location in Redis: driverId={}, lat={}, lng={}", driverId, lat, lng);

        if (rideId != null) {
            try {
                double etaMinutes = computeSimpleEta(lat, lng);
                Map<String, Object> payload = new HashMap<>();
                payload.put("lat", lat);
                payload.put("lng", lng);
                payload.put("eta", etaMinutes);
                payload.put("driverId", driverId);

                String locationJson = objectMapper.writeValueAsString(payload);
                String channel = "ride:" + rideId + ":location";
                redisTemplate.convertAndSend(channel, locationJson);

                log.debug("Published location to channel {}: {}", channel, locationJson);
            } catch (Exception e) {
                log.error("Error publishing location for ride {}: {}", rideId, e.getMessage(), e);
            }
        }
    }

    public void removeDriverLocation(String driverId) {
        String key = "driver:location:" + driverId;
        redisTemplate.delete(key);
        log.info("Removed driver location from Redis: driverId={}", driverId);
    }

    public Map<String, String> getDriverLocation(String driverId) {
        String key = "driver:location:" + driverId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, String> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }

    /**
     * Computes a simple ETA in minutes based on a rough distance estimate.
     * Uses the formula: ETA = (distance_km / speed_kmh) * 60 minutes.
     * The distance is approximated as the Euclidean distance of lat/lng degrees
     * converted to km (1 degree ~ 111 km).
     */
    private double computeSimpleEta(double lat, double lng) {
        // Simple approximation: use distance from origin as a proxy
        // In a real system, this would compute distance to the rider's pickup point
        double distanceKm = Math.sqrt(lat * lat + lng * lng) * 111.0 / 1000.0;
        double etaMinutes = (distanceKm / AVERAGE_SPEED_KMH) * 60.0;
        return Math.round(etaMinutes * 100.0) / 100.0;
    }
}
