package com.rideflow.pricingservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeohashUtilTest {

    @Test
    void encode_bengaluruCoordinates_returnsSixCharGeohash() {
        String geohash = GeohashUtil.encode(12.9716, 77.5946, 6);

        assertThat(geohash).isNotNull();
        assertThat(geohash).hasSize(6);
    }

    @Test
    void encode_sameLocation_returnsSameGeohash() {
        String first = GeohashUtil.encode(12.9716, 77.5946, 6);
        String second = GeohashUtil.encode(12.9716, 77.5946, 6);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void encode_differentPrecision_returnsDifferentLength() {
        String precision4 = GeohashUtil.encode(12.9716, 77.5946, 4);
        String precision6 = GeohashUtil.encode(12.9716, 77.5946, 6);

        assertThat(precision4).hasSize(4);
        assertThat(precision6).hasSize(6);
        assertThat(precision4.length()).isNotEqualTo(precision6.length());
    }
}
