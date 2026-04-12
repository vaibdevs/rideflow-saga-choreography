package com.rideflow.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// TODO: Replace with FirebaseMessaging.getInstance().send() in production
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final SmsService smsService;

    public boolean send(String userId, String message) {
        int maxRetries = 3;
        long backoff = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Mock FCM push notification
                log.info("Sending push notification to userId={}: {}", userId, message);
                // Simulate successful push
                log.info("Push notification sent successfully to userId={}", userId);
                return true;
            } catch (Exception e) {
                log.warn("Push notification attempt {}/{} failed for userId={}: {}",
                        attempt, maxRetries, userId, e.getMessage());
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

        log.warn("All push notification retries failed for userId={}, falling back to SMS", userId);
        return smsService.send(userId, message);
    }
}
