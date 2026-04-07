package com.rideflow.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRideRequest {

    @NotNull(message = "Rider ID is required")
    private UUID riderId;

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLat;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLng;

    @NotNull(message = "Drop latitude is required")
    private Double dropLat;

    @NotNull(message = "Drop longitude is required")
    private Double dropLng;
}
