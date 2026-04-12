package com.rideflow.notificationservice.template;

import com.rideflow.notificationservice.exception.TemplateNotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TemplateEngine {

    private static final Map<String, String> TEMPLATES = new HashMap<>();

    static {
        TEMPLATES.put("driver.assigned:RIDER",
                "Driver {driverName} is on the way! ETA {eta} mins. Vehicle: {vehicle}");
        TEMPLATES.put("driver.assigned:DRIVER",
                "New ride accepted. Navigate to pickup.");
        TEMPLATES.put("ride.started:RIDER",
                "Your ride has started. Enjoy the trip!");
        TEMPLATES.put("ride.completed:RIDER",
                "You have arrived! Fare: ₹{fare}");
        TEMPLATES.put("payment.success:RIDER",
                "₹{amount} paid successfully. Receipt: #{txnId}");
        TEMPLATES.put("payment.failed:RIDER",
                "Payment failed. Please update your payment method.");
        TEMPLATES.put("driver.unavailable:RIDER",
                "No drivers nearby. Please try again in a few minutes.");
        TEMPLATES.put("ride.cancelled:RIDER",
                "Your ride was cancelled. We are finding you another driver.");
        TEMPLATES.put("ride.cancelled:DRIVER",
                "Ride cancelled. Repeated cancellations affect your rating.");
        TEMPLATES.put("payment.refunded:RIDER",
                "₹{amount} has been refunded to your account. Reason: {reason}");
    }

    public String resolve(String eventType, String role) {
        String key = eventType + ":" + role;
        String template = TEMPLATES.get(key);
        if (template == null) {
            throw new TemplateNotFoundException("No template found for " + key);
        }
        return template;
    }

    public String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
