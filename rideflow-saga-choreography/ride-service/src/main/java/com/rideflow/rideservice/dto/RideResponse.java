package com.rideflow.rideservice.dto;

import com.rideflow.rideservice.domain.RideStatus;
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
public class RideResponse {

    private UUID id;
    private UUID riderId;
    private UUID driverId;
    private RideStatus status;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private BigDecimal fareEstimate;
    private BigDecimal fareActual;
    private String cancelledBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
