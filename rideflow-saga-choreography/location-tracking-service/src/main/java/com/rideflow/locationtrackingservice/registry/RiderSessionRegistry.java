package com.rideflow.locationtrackingservice.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RiderSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String rideId, WebSocketSession session) {
        sessions.put(rideId, session);
        log.info("Registered rider session for rideId={}", rideId);
    }

    public void deregister(String rideId) {
        sessions.remove(rideId);
        log.info("Deregistered rider session for rideId={}", rideId);
    }

    public WebSocketSession getSession(String rideId) {
        return sessions.get(rideId);
    }

    public void pushToRider(String rideId, String locationJson) {
        WebSocketSession session = sessions.get(rideId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(locationJson));
                log.debug("Pushed location to rider for rideId={}", rideId);
            } catch (IOException e) {
                log.error("Error pushing location to rider for rideId={}: {}", rideId, e.getMessage(), e);
            }
        } else {
            log.debug("No open session found for rideId={}", rideId);
        }
    }
}
