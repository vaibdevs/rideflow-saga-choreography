package com.rideflow.notificationservice.template;

import com.rideflow.notificationservice.exception.TemplateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateEngineTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
    }

    @Test
    @DisplayName("resolve - driver.assigned with RIDER role - returns template containing 'on the way'")
    void resolve_driverAssignedRiderRole_returnsCorrectTemplate() {
        String template = templateEngine.resolve("driver.assigned", "RIDER");

        assertThat(template).contains("on the way");
    }

    @Test
    @DisplayName("resolve - driver.assigned with DRIVER role - returns template for driver")
    void resolve_driverAssignedDriverRole_returnsCorrectTemplate() {
        String template = templateEngine.resolve("driver.assigned", "DRIVER");

        assertThat(template).contains("New ride accepted");
    }

    @Test
    @DisplayName("resolve - payment.success with RIDER role - returns payment template")
    void resolve_paymentSuccessRiderRole_returnsPaymentTemplate() {
        String template = templateEngine.resolve("payment.success", "RIDER");

        assertThat(template).contains("paid successfully");
    }

    @Test
    @DisplayName("resolve - driver.unavailable with RIDER role - returns unavailable template")
    void resolve_driverUnavailableRiderRole_returnsUnavailableTemplate() {
        String template = templateEngine.resolve("driver.unavailable", "RIDER");

        assertThat(template).contains("No drivers nearby");
    }

    @Test
    @DisplayName("render - template with variables - interpolates correctly")
    void render_templateWithVariables_interpolatesCorrectly() {
        String template = "Hello {name}, your ride costs {fare}";
        Map<String, String> variables = Map.of("name", "John", "fare", "250");

        String result = templateEngine.render(template, variables);

        assertThat(result).isEqualTo("Hello John, your ride costs 250");
    }

    @Test
    @DisplayName("render - template with null variable value - replaces with empty string")
    void render_templateWithNullValue_replacesWithEmptyString() {
        String template = "Hello {name}";
        Map<String, String> variables = Collections.singletonMap("name", null);

        String result = templateEngine.render(template, variables);

        assertThat(result).isEqualTo("Hello ");
    }

    @Test
    @DisplayName("render - template with no matching variables - returns template unchanged")
    void render_templateWithNoMatchingVariables_returnsUnchanged() {
        String template = "Hello {name}";
        Map<String, String> variables = Map.of("city", "Bangalore");

        String result = templateEngine.render(template, variables);

        assertThat(result).isEqualTo("Hello {name}");
    }

    @Test
    @DisplayName("resolve - unknown event type - throws TemplateNotFoundException")
    void resolve_unknownEventType_throwsTemplateNotFoundException() {
        assertThatThrownBy(() -> templateEngine.resolve("unknown.event", "RIDER"))
                .isInstanceOf(TemplateNotFoundException.class)
                .hasMessageContaining("No template found for unknown.event:RIDER");
    }

    @Test
    @DisplayName("resolve - valid event type with invalid role - throws TemplateNotFoundException")
    void resolve_validEventInvalidRole_throwsTemplateNotFoundException() {
        assertThatThrownBy(() -> templateEngine.resolve("driver.assigned", "ADMIN"))
                .isInstanceOf(TemplateNotFoundException.class)
                .hasMessageContaining("No template found for driver.assigned:ADMIN");
    }
}
