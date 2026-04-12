package com.rideflow.paymentservice.kafka.event;

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
public class RideCancelledEvent {

    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private String cancelledBy;
    private String previousStatus;
    private LocalDateTime createdAt;
    private LocalDateTime timestamp;
}
