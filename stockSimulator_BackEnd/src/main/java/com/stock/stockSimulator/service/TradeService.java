package com.stock.stockSimulator.service;

import com.stock.stockSimulator.component.AsyncTradeLogger;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Service
@RequiredArgsConstructor
public class TradeService {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tradeScript;
    private final AsyncTradeLogger asyncTradeLogger;

    @Transactional
    public String executeMarketOrder(String stockCode, int price, int qty, String buyerId) {
        String orderQueueKey = "orders:" + stockCode + ":" + price;

        // [Step 1] Redis Lua Script 실행
        // 반환: [Long totalQty, List<String> matchedOrderInfos]
        List<Object> result = redisTemplate.execute(tradeScript,
                Collections.singletonList(orderQueueKey),
                String.valueOf(qty), buyerId);

        if (result == null || (Long) result.get(0) == 0) {
            return "체결 가능한 매도 물량이 없습니다.";
        }

        Long totalExecutedQty = (Long) result.get(0);
        List<String> matchedOrders = (List<String>) result.get(1);

        // [Step 2] 체결 건별 DB 비동기 기록 및 알림
        for (String orderInfo : matchedOrders) {
            String[] parts = orderInfo.split(":"); // orderId, sellerId, qty
            asyncTradeLogger.recordTrade(Long.parseLong(buyerId), Long.parseLong(parts[1]), Long.parseLong(parts[0]), stockCode, price, Integer.parseInt(parts[2]));
        }

        // [Step 3] 실시간 호가 전광판 Pub/Sub 알림
        redisTemplate.convertAndSend("market-broadcast", stockCode + ":" + price + ":update");

        return totalExecutedQty + "주 체결 완료 (상세 로그 DB 기록 중)";
    }
}
