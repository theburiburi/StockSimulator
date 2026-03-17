package com.stock.stockSimulator.service;

import com.stock.stockSimulator.domain.Stock;
import com.stock.stockSimulator.repository.StockRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;
    private final RedisService redisService;

    @Transactional
    public void closeMarket(){
        List<Stock> stocks = stockRepository.findAll();
        for(Stock stock : stocks){
            Long finalPrice = redisService.getStockPrice(stock.getCode());

            stock.setEndingPrice(finalPrice);
            stock.setCurrentPrice(finalPrice);
        }
    }
}
