package com.stock.stockSimulator.scheduler;

import com.stock.stockSimulator.repository.StockRepository;
import com.stock.stockSimulator.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
@RequiredArgsConstructor
public class StockPriceScheduler {
    private final StockRepository stockRepository;
    private final RedisService redisService;
    private final Random random = new Random();
    private final SimpMessageSendingOperations messageTemplate;

    @Scheduled(fixedRate = 1000)
    public void updatePrice(){
        stockRepository.findAll().forEach(stock -> {
            Long currentPrice = redisService.getStockPrice(stock.getCode());
            System.out.println(stock.getName() + " " + currentPrice);
            if(currentPrice == 0) currentPrice = stock.getOpeningPrice();

            double volatility = 0.001;
            double move = random.nextGaussian() * volatility;

            long nextPrice = (long) (currentPrice * (1+move));

            redisService.updateStockInfo(stock.getCode(), nextPrice, stock.getOpeningPrice());
            stock.setCurrentPrice(nextPrice);
            messageTemplate.convertAndSend("/topic/stock", stock);

            double rate = ((double)(nextPrice - stock.getOpeningPrice()) / stock.getOpeningPrice()) * 100;
            System.out.printf("[%s] 현재가: %d (%+.2f%%)%n", stock.getName(), nextPrice, rate);
        });
    }
}
