package com.rideflow.drivermatchingservice.kafka.event;

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
public class DriverUnavailableEvent {

    private UUID rideId;
    private UUID riderId;
    private String reason;
    private LocalDateTime timestamp;
}
