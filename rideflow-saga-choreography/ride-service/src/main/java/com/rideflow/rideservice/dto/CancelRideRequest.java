package com.rideflow.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRideRequest {

    @NotBlank(message = "cancelledBy is required (RIDER or DRIVER)")
    private String cancelledBy;
}
