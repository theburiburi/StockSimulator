package com.stock.stockSimulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public DefaultRedisScript<List> tradeScript(){
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/match.lua"));
        script.setResultType(List.class);

        return script;
    }

    @Bean
    public Executor taskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 유지 스레드 수
        executor.setMaxPoolSize(20); // 최대 확장 스레드 수
        executor.setQueueCapacity(1000); // 대기 큐 크기
        executor.setThreadNamePrefix("TradeEngine-");
        executor.initialize();

        return executor;
    }
}
