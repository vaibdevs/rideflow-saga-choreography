package com.rideflow.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private SmsService smsService;

    @InjectMocks
    private PushNotificationService pushNotificationService;

    @Test
    @DisplayName("send - successful push notification - returns true")
    void send_success_returnsTrue() {
        boolean result = pushNotificationService.send("user-123", "Your ride is confirmed!");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("send - successful push notification - does not fall back to SMS")
    void send_success_doesNotFallBackToSms() {
        pushNotificationService.send("user-123", "Your ride is confirmed!");

        verify(smsService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("send - multiple successful calls - each returns true independently")
    void send_multipleSuccessfulCalls_eachReturnsTrue() {
        boolean result1 = pushNotificationService.send("user-1", "Message 1");
        boolean result2 = pushNotificationService.send("user-2", "Message 2");

        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        verify(smsService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("send - with empty message - still succeeds")
    void send_emptyMessage_stillSucceeds() {
        boolean result = pushNotificationService.send("user-123", "");

        assertThat(result).isTrue();
        verify(smsService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("send - on success path - SMS service is never invoked")
    void send_successPath_smsServiceNeverInvoked() {
        boolean result = pushNotificationService.send("rider-456", "Driver is on the way!");

        assertThat(result).isTrue();
        verifyNoInteractions(smsService);
    }
}
