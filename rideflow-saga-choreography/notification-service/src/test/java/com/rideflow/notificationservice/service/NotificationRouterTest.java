package com.rideflow.notificationservice.service;

import com.rideflow.notificationservice.template.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private NotificationRouter notificationRouter;

    @Test
    @DisplayName("notifyBoth - driver.assigned event - notifies both rider and driver")
    void route_driverAssignedEvent_notifiesBothRiderAndDriver() {
        String riderId = "rider-123";
        String driverId = "driver-456";
        Map<String, String> vars = Map.of("driverName", "John", "eta", "5", "vehicle", "KA01AB1234");

        when(templateEngine.resolve("driver.assigned", "RIDER"))
                .thenReturn("Driver {driverName} is on the way! ETA {eta} mins. Vehicle: {vehicle}");
        when(templateEngine.resolve("driver.assigned", "DRIVER"))
                .thenReturn("New ride accepted. Navigate to pickup.");
        when(templateEngine.render(anyString(), eq(vars)))
                .thenAnswer(invocation -> {
                    String template = invocation.getArgument(0);
                    return template.replace("{driverName}", "John")
                            .replace("{eta}", "5")
                            .replace("{vehicle}", "KA01AB1234");
                });
        when(pushNotificationService.send(anyString(), anyString())).thenReturn(true);

        notificationRouter.notifyBoth("driver.assigned", riderId, driverId, vars);

        verify(pushNotificationService).send(eq(riderId), anyString());
        verify(pushNotificationService).send(eq(driverId), anyString());
        verify(pushNotificationService, times(2)).send(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyRider - payment.success event - notifies only rider")
    void route_paymentSuccessEvent_notifiesOnlyRider() {
        String riderId = "rider-123";
        Map<String, String> vars = Map.of("amount", "250", "txnId", "TXN001");

        when(templateEngine.resolve("payment.success", "RIDER"))
                .thenReturn("Rs.{amount} paid successfully. Receipt: #{txnId}");
        when(templateEngine.render(anyString(), eq(vars)))
                .thenReturn("Rs.250 paid successfully. Receipt: #TXN001");
        when(pushNotificationService.send(anyString(), anyString())).thenReturn(true);

        notificationRouter.notifyRider("payment.success", riderId, vars);

        verify(pushNotificationService, times(1)).send(eq(riderId), anyString());
    }

    @Test
    @DisplayName("notifyRider - driver.unavailable event - notifies only rider")
    void route_driverUnavailableEvent_notifiesOnlyRider() {
        String riderId = "rider-456";
        Map<String, String> vars = Map.of();

        when(templateEngine.resolve("driver.unavailable", "RIDER"))
                .thenReturn("No drivers nearby. Please try again in a few minutes.");
        when(templateEngine.render(anyString(), eq(vars)))
                .thenReturn("No drivers nearby. Please try again in a few minutes.");
        when(pushNotificationService.send(anyString(), anyString())).thenReturn(true);

        notificationRouter.notifyRider("driver.unavailable", riderId, vars);

        verify(pushNotificationService, times(1)).send(eq(riderId),
                eq("No drivers nearby. Please try again in a few minutes."));
    }

    @Test
    @DisplayName("notifyDriver - ride.cancelled event - notifies only driver")
    void route_rideCancelledEvent_notifiesOnlyDriver() {
        String driverId = "driver-789";
        Map<String, String> vars = Map.of();

        when(templateEngine.resolve("ride.cancelled", "DRIVER"))
                .thenReturn("Ride cancelled. Repeated cancellations affect your rating.");
        when(templateEngine.render(anyString(), eq(vars)))
                .thenReturn("Ride cancelled. Repeated cancellations affect your rating.");
        when(pushNotificationService.send(anyString(), anyString())).thenReturn(true);

        notificationRouter.notifyDriver("ride.cancelled", driverId, vars);

        verify(pushNotificationService, times(1)).send(eq(driverId), anyString());
    }
}
