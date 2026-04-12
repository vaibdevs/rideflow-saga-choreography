package com.rideflow.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

// TODO: Replace with Razorpay SDK in production
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.mock", havingValue = "true", matchIfMissing = true)
public class MockPaymentGatewayAdapter implements PaymentGateway {

    private final Random random = new Random();

    @Override
    public ChargeResponse charge(ChargeRequest request) {
        log.info("Mock payment gateway: charging {} {} for key={}",
                request.amount(), request.currency(), request.idempotencyKey());

        // Simulate 90% success, 10% failure
        boolean success = random.nextInt(10) < 9;

        if (success) {
            String txnId = "mock_txn_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("Mock charge successful: txnId={}", txnId);
            return new ChargeResponse(true, txnId, null);
        } else {
            log.warn("Mock charge failed for key={}", request.idempotencyKey());
            return new ChargeResponse(false, null, "Insufficient funds (mock)");
        }
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        log.info("Mock payment gateway: refunding {} for originalTxn={}",
                request.amount(), request.originalTransactionId());

        String refundTxnId = "mock_refund_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Mock refund successful: refundTxnId={}", refundTxnId);
        return new RefundResponse(true, refundTxnId, null);
    }
}
