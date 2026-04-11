package com.rideflow.locationtrackingservice.config;

import com.rideflow.locationtrackingservice.handler.DriverLocationWebSocketHandler;
import com.rideflow.locationtrackingservice.handler.RiderLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DriverLocationWebSocketHandler driverLocationWebSocketHandler;
    private final RiderLocationWebSocketHandler riderLocationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(driverLocationWebSocketHandler, "/ws/driver/**")
                .setAllowedOrigins("*")
                .withSockJS();

        registry.addHandler(riderLocationWebSocketHandler, "/ws/rider/**")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
