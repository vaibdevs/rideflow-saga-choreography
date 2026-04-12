package com.rideflow.paymentservice.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {

    ChargeResponse charge(ChargeRequest request);

    RefundResponse refund(RefundRequest request);

    record ChargeRequest(String idempotencyKey, BigDecimal amount, String currency, String description) {}

    record ChargeResponse(boolean success, String transactionId, String failureReason) {}

    record RefundRequest(String originalTransactionId, BigDecimal amount, String reason) {}

    record RefundResponse(boolean success, String refundTransactionId, String failureReason) {}
}
