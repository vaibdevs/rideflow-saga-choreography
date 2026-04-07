package com.rideflow.rideservice.kafka.event;

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
public class DriverAssignedEvent {

    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private String driverName;
    private String vehicleNumber;
    private String vehicleType;
    private Double driverLat;
    private Double driverLng;
    private Double etaMinutes;
    private LocalDateTime timestamp;
}
