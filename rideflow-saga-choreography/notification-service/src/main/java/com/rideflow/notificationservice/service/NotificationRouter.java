package com.rideflow.notificationservice.service;

import com.rideflow.notificationservice.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouter {

    private final TemplateEngine templateEngine;
    private final PushNotificationService pushNotificationService;

    public void notifyRider(String eventType, String riderId, Map<String, String> variables) {
        try {
            String template = templateEngine.resolve(eventType, "RIDER");
            String message = templateEngine.render(template, variables);
            pushNotificationService.send(riderId, message);
            log.info("Notified RIDER {} for event {}", riderId, eventType);
        } catch (Exception e) {
            log.error("Failed to notify RIDER {} for event {}: {}", riderId, eventType, e.getMessage());
        }
    }

    public void notifyDriver(String eventType, String driverId, Map<String, String> variables) {
        try {
            String template = templateEngine.resolve(eventType, "DRIVER");
            String message = templateEngine.render(template, variables);
            pushNotificationService.send(driverId, message);
            log.info("Notified DRIVER {} for event {}", driverId, eventType);
        } catch (Exception e) {
            log.error("Failed to notify DRIVER {} for event {}: {}", driverId, eventType, e.getMessage());
        }
    }

    public void notifyBoth(String eventType, String riderId, String driverId, Map<String, String> variables) {
        notifyRider(eventType, riderId, variables);
        notifyDriver(eventType, driverId, variables);
    }
}
