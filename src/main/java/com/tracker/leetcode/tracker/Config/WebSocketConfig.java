package com.tracker.leetcode.tracker.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // This is the prefix for outgoing messages from the server to the client
        config.enableSimpleBroker("/topic");
        // This is the prefix for incoming messages from the client (we won't use this much yet)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The endpoint our React app will connect to
        registry.addEndpoint("/ws-endpoint")
                .setAllowedOrigins("http://localhost:5173")
                .withSockJS(); // Fallback for browsers that don't support raw WebSockets
    }
}