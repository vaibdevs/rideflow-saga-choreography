package com.rideflow.drivermatchingservice.dto;

import com.rideflow.drivermatchingservice.domain.DriverStatus;
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
public class DriverResponse {

    private UUID id;
    private String name;
    private String phoneNumber;
    private String vehicleNumber;
    private String vehicleType;
    private Double rating;
    private DriverStatus status;
    private Double currentLat;
    private Double currentLng;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
