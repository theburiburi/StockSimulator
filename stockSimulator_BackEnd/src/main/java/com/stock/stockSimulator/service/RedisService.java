package com.stock.stockSimulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final StringRedisTemplate redisTemplate;

    private String getStockKey(String code) {
        return "STOCK:" + code;
    }

    public void setStockPrice(String code, Long price){
        redisTemplate.opsForHash().put(getStockKey(code), "current", String.valueOf(price));
    }

    public Long getStockPrice(String code){
        Object value = redisTemplate.opsForHash().get(getStockKey(code), "current");
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    public void updateStockInfo(String code, Long current, Long open){
        String key = getStockKey(code);
        double changeRate = ((double) (current - open) /  open) * 100;

        Map<String, String> data = new HashMap<>();
        data.put("current", String.valueOf(current));
        data.put("open", String.valueOf(open));
        data.put("rate", String.format("%.2f", changeRate));

        redisTemplate.opsForHash().putAll(key, data);
    }
}