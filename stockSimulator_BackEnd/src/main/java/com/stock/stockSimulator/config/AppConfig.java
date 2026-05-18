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
    public DefaultRedisScript<List> matchEngineScript(){
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/match_engine.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> cancelOrderScript(){
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/cancel_order.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public Executor taskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50); // 기본 유지 스레드 수
        executor.setMaxPoolSize(100); // 최대 확장 스레드 수
        executor.setQueueCapacity(100000); // 대기 큐 크기 (충분한 버퍼 확보)
        executor.setThreadNamePrefix("TradeEngine-");
        // 대기 큐 포화 시 호출자 스레드(Tomcat)가 직접 실행하도록 하여 우아한 백프레셔 구현 (요청 거부 원천 방지)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
