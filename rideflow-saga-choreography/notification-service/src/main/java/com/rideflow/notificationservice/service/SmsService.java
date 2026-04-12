package com.rideflow.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// TODO: Replace with Twilio Message.creator() in production
@Slf4j
@Service
public class SmsService {

    public boolean send(String userId, String message) {
        int maxRetries = 3;
        long backoff = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Mock Twilio SMS
                log.info("Sending SMS to userId={}: {}", userId, message);
                log.info("SMS sent successfully to userId={}", userId);
                return true;
            } catch (Exception e) {
                log.warn("SMS attempt {}/{} failed for userId={}: {}", attempt, maxRetries, userId, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff *= 2;
                }
            }
        }

        log.error("All SMS retries failed for userId={}", userId);
        return false;
    }
}
