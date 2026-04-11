package com.rideflow.locationtrackingservice.service;

import com.rideflow.locationtrackingservice.registry.RiderSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPubSubSubscriber implements MessageListener {

    private final RiderSessionRegistry riderSessionRegistry;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private final Map<String, ChannelTopic> activeSubscriptions = new ConcurrentHashMap<>();

    public void subscribe(String rideId) {
        String channel = "ride:" + rideId + ":location";
        ChannelTopic topic = new ChannelTopic(channel);
        redisMessageListenerContainer.addMessageListener(this, topic);
        activeSubscriptions.put(rideId, topic);
        log.info("Subscribed to Redis channel: {}", channel);
    }

    public void unsubscribe(String rideId) {
        ChannelTopic topic = activeSubscriptions.remove(rideId);
        if (topic != null) {
            redisMessageListenerContainer.removeMessageListener(this, topic);
            log.info("Unsubscribed from Redis channel: {}", topic.getTopic());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        // Extract rideId from channel pattern: ride:{rideId}:location
        String rideId = extractRideIdFromChannel(channel);
        if (rideId != null) {
            log.debug("Received Redis message on channel {}: {}", channel, body);
            riderSessionRegistry.pushToRider(rideId, body);
        } else {
            log.warn("Could not extract rideId from channel: {}", channel);
        }
    }

    private String extractRideIdFromChannel(String channel) {
        // Channel format: ride:{rideId}:location
        if (channel != null && channel.startsWith("ride:") && channel.endsWith(":location")) {
            return channel.substring("ride:".length(), channel.length() - ":location".length());
        }
        return null;
    }
}
