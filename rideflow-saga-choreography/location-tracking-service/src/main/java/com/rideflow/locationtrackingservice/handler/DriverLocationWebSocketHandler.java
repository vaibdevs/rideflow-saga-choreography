package com.rideflow.locationtrackingservice.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.locationtrackingservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationWebSocketHandler extends TextWebSocketHandler {

    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    private final Map<String, String> sessionDriverMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String driverId = extractDriverIdFromUri(session.getUri());
        if (driverId != null) {
            sessionDriverMap.put(session.getId(), driverId);
            log.info("Driver WebSocket connected: driverId={}, sessionId={}", driverId, session.getId());
        } else {
            log.warn("Could not extract driverId from URI: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String driverId = sessionDriverMap.get(session.getId());
        if (driverId == null) {
            log.warn("Received message from unknown session: {}", session.getId());
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            double lat = json.get("lat").asDouble();
            double lng = json.get("lng").asDouble();
            String rideId = json.has("rideId") && !json.get("rideId").isNull()
                    ? json.get("rideId").asText()
                    : null;

            locationService.updateDriverLocation(driverId, lat, lng, rideId);
            log.debug("Location update: driverId={}, lat={}, lng={}, rideId={}", driverId, lat, lng, rideId);
        } catch (Exception e) {
            log.error("Error processing location message from driver {}: {}", driverId, e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String driverId = sessionDriverMap.remove(session.getId());
        if (driverId != null) {
            locationService.removeDriverLocation(driverId);
            log.info("Driver WebSocket disconnected: driverId={}, status={}", driverId, status);
        }
    }

    private String extractDriverIdFromUri(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath();
        // Expected path: /ws/driver/{driverId}
        String[] segments = path.split("/");
        if (segments.length >= 4 && "driver".equals(segments[2])) {
            return segments[3];
        }
        return null;
    }
}
