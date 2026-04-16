package com.rideflow.drivermatchingservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoSearchServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private GeoSearchService geoSearchService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        geoSearchService = new GeoSearchService(redisTemplate);
    }

    @Test
    @DisplayName("addDriverLocation - valid location - stored in Redis geo index")
    void addDriver_validLocation_storedInRedis() {
        geoSearchService.addDriverLocation("driver1", 12.97, 77.59);

        verify(geoOperations).add(
                eq("drivers:locations"),
                eq(new Point(77.59, 12.97)),
                eq("driver1")
        );
    }

    @Test
    @DisplayName("findNearestDrivers - drivers within radius - returns drivers sorted by distance")
    void findNearestDrivers_driversWithinRadius_returnsDriversSortedByDistance() {
        GeoResult<RedisGeoCommands.GeoLocation<String>> result1 =
                new GeoResult<>(new RedisGeoCommands.GeoLocation<>("driver1", new Point(77.59, 12.97)),
                        new Distance(1.0, Metrics.KILOMETERS));
        GeoResult<RedisGeoCommands.GeoLocation<String>> result2 =
                new GeoResult<>(new RedisGeoCommands.GeoLocation<>("driver2", new Point(77.60, 12.98)),
                        new Distance(2.0, Metrics.KILOMETERS));

        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
                new GeoResults<>(Arrays.asList(result1, result2));

        when(geoOperations.radius(
                eq("drivers:locations"),
                any(Circle.class),
                any(RedisGeoCommands.GeoRadiusCommandArgs.class)
        )).thenReturn(geoResults);

        List<String> drivers = geoSearchService.findNearestDrivers(12.97, 77.59, 3, 3);

        assertThat(drivers).hasSize(2);
        assertThat(drivers).containsExactly("driver1", "driver2");
    }

    @Test
    @DisplayName("findNearestDrivers - no drivers in radius - returns empty list")
    void findNearestDrivers_noDriversInRadius_returnsEmptyList() {
        GeoResults<RedisGeoCommands.GeoLocation<String>> emptyResults =
                new GeoResults<>(Collections.emptyList());

        when(geoOperations.radius(
                eq("drivers:locations"),
                any(Circle.class),
                any(RedisGeoCommands.GeoRadiusCommandArgs.class)
        )).thenReturn(emptyResults);

        List<String> drivers = geoSearchService.findNearestDrivers(12.97, 77.59, 3, 3);

        assertThat(drivers).isEmpty();
    }

    @Test
    @DisplayName("findNearestDrivers - null result from Redis - returns empty list")
    void findNearestDrivers_nullResult_returnsEmptyList() {
        when(geoOperations.radius(
                eq("drivers:locations"),
                any(Circle.class),
                any(RedisGeoCommands.GeoRadiusCommandArgs.class)
        )).thenReturn(null);

        List<String> drivers = geoSearchService.findNearestDrivers(12.97, 77.59, 3, 3);

        assertThat(drivers).isEmpty();
    }

    @Test
    @DisplayName("removeDriverLocation - driver goes offline - removed from geo index")
    void removeDriver_driverGoesOffline_removedFromGeoIndex() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        geoSearchService.removeDriverLocation("driver1");

        verify(redisTemplate.opsForZSet()).remove("drivers:locations", "driver1");
    }
}
