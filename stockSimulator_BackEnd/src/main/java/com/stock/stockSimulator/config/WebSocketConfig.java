package com.stock.stockSimulator.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws-stock")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        // server -> client (broadcast: /topic, personal: /queue)
        registry.enableSimpleBroker("/topic", "/queue");
        // convertAndSendToUser() uses /user/{principal}/queue/...
        registry.setUserDestinationPrefix("/user");

        // client -> server
        registry.setApplicationDestinationPrefixes("/app");
    }
}
