package com.rideflow.locationtrackingservice.handler;

import com.rideflow.locationtrackingservice.registry.RiderSessionRegistry;
import com.rideflow.locationtrackingservice.service.RedisPubSubSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiderLocationWebSocketHandler extends TextWebSocketHandler {

    private final RiderSessionRegistry riderSessionRegistry;
    private final RedisPubSubSubscriber redisPubSubSubscriber;

    private final Map<String, String> sessionRideMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String rideId = extractRideIdFromUri(session.getUri());
        if (rideId != null) {
            sessionRideMap.put(session.getId(), rideId);
            riderSessionRegistry.register(rideId, session);
            redisPubSubSubscriber.subscribe(rideId);
            log.info("Rider WebSocket connected: rideId={}, sessionId={}", rideId, session.getId());
        } else {
            log.warn("Could not extract rideId from URI: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String rideId = sessionRideMap.remove(session.getId());
        if (rideId != null) {
            riderSessionRegistry.deregister(rideId);
            redisPubSubSubscriber.unsubscribe(rideId);
            log.info("Rider WebSocket disconnected: rideId={}, status={}", rideId, status);
        }
    }

    private String extractRideIdFromUri(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath();
        // Expected path: /ws/rider/{rideId}
        String[] segments = path.split("/");
        if (segments.length >= 4 && "rider".equals(segments[2])) {
            return segments[3];
        }
        return null;
    }
}
