package com.stock.stockSimulator.config;

import com.stock.stockSimulator.component.RedisSubcriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    // Redis Pub/Sub 메시지를 듣고 Subscriber에게 전달하는 컨테이너
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic("market-data"));
        return container;
    }

    // Subscriber를 어댑터에 등록
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubcriber subscriber) {
        return new MessageListenerAdapter(subscriber);
    }
}
