package com.stock.stockSimulator.component;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubcriber implements MessageListener {
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern){
        try{
            String publishMessage = new String(message.getBody());

            log.info("Redis Pub/Sub 수신 내역: {}", publishMessage);

            messagingTemplate.convertAndSend("/topic/market", publishMessage);
        } catch (Exception e){
            log.error("RedisSubscriber에서 메시지 처리 중 오류 발생: {}", e.getMessage());
        }
    }
}
