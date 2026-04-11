package com.rideflow.drivermatchingservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeoSearchService {

    private static final String GEO_KEY = "drivers:locations";

    private final GeoOperations<String, String> geoOperations;
    private final RedisTemplate<String, String> redisTemplate;

    public GeoSearchService(RedisTemplate<String, String> stringRedisTemplate) {
        this.redisTemplate = stringRedisTemplate;
        this.geoOperations = stringRedisTemplate.opsForGeo();
    }

    public void addDriverLocation(String driverId, double lat, double lng) {
        log.info("Adding driver location to geo index: driverId={}, lat={}, lng={}", driverId, lat, lng);
        geoOperations.add(GEO_KEY, new Point(lng, lat), driverId);
    }

    public void removeDriverLocation(String driverId) {
        log.info("Removing driver location from geo index: driverId={}", driverId);
        redisTemplate.opsForZSet().remove(GEO_KEY, driverId);
    }

    @CircuitBreaker(name = "geoSearch", fallbackMethod = "geoSearchFallback")
    public List<String> findNearestDrivers(double lat, double lng, double radiusKm, int count) {
        log.info("Finding nearest drivers: lat={}, lng={}, radiusKm={}, count={}", lat, lng, radiusKm, count);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geoOperations.radius(
                GEO_KEY,
                new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .sortAscending()
                        .limit(count)
        );

        if (results == null) {
            log.warn("No geo results returned for lat={}, lng={}", lat, lng);
            return Collections.emptyList();
        }

        List<String> driverIds = results.getContent().stream()
                .map(result -> result.getContent().getName())
                .collect(Collectors.toList());

        log.info("Found {} nearby drivers", driverIds.size());
        return driverIds;
    }

    @SuppressWarnings("unused")
    private List<String> geoSearchFallback(double lat, double lng, double radiusKm, int count, Throwable t) {
        log.error("Circuit breaker triggered for geo search: lat={}, lng={}, error={}", lat, lng, t.getMessage());
        return Collections.emptyList();
    }
}
