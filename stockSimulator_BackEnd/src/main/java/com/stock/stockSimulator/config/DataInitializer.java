package com.stock.stockSimulator.config;

import com.stock.stockSimulator.domain.Stock;
import com.stock.stockSimulator.repository.StockRepository;
import com.stock.stockSimulator.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StockRepository stockRepository;
    private final RedisService redisService;

    @Override
    public void run(String... args) {
        // 1. DB에 종목 데이터가 없을 때만 초기 데이터 생성
        if (stockRepository.count() == 0) {
            List<Stock> initialStocks = Arrays.asList(
                    new Stock("005930", "삼성전자", 75000L, 75000L, 74200L),
                    new Stock("000660", "SK하이닉스", 185000L, 185000L, 182000L),
                    new Stock("AAPL", "Apple", 215000L, 215000L, 210000L),
                    new Stock("TSLA", "Tesla", 245000L, 245000L, 240000L),
                    new Stock("NVDA", "NVIDIA", 125000L, 125000L, 120000L)
            );
            stockRepository.saveAll(initialStocks);
            System.out.println("✅ [DB] 5개의 기초 종목 데이터가 저장되었습니다.");
        }

        // 2. DB에 있는 모든 데이터를 Redis로 로딩 (Hash 구조 업데이트)
        stockRepository.findAll().forEach(stock -> {
            redisService.updateStockInfo(
                    stock.getCode(),
                    stock.getCurrentPrice(),
                    stock.getOpeningPrice()
            );
        });

        System.out.println("🚀 [Redis] 모든 종목이 실시간 전광판(Redis)에 등록되었습니다!");
    }
}