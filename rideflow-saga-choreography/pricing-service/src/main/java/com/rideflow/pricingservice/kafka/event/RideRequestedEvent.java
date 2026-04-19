package com.rideflow.pricingservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestedEvent {

    private UUID rideId;
    private UUID riderId;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private LocalDateTime timestamp;
}
