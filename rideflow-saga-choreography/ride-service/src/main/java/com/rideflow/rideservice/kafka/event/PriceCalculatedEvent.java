package com.rideflow.rideservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculatedEvent {

    private UUID rideId;
    private UUID riderId;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private BigDecimal fareEstimate;
    private Double surgeMultiplier;
    private Double distanceKm;
    private Double etaMinutes;
    private LocalDateTime timestamp;
}
