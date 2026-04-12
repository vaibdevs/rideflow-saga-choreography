package com.rideflow.paymentservice.kafka.event;

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
public class PaymentSuccessEvent {

    private UUID paymentId;
    private UUID rideId;
    private UUID riderId;
    private BigDecimal amount;
    private String gatewayTxnId;
    private LocalDateTime timestamp;
}
