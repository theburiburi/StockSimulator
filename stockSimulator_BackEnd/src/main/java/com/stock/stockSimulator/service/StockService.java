package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.Stock;
import com.stock.stockSimulator.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주식 관련 비즈니스 로직 담당
 * SRP: 주식 조회, 가격 갱신, 장 마감 책임만 가짐
 * DIP: StockController가 Repository가 아닌 StockService에 의존하도록 변경
 */
@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository stockRepository;
    private final RedisService redisService;

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    /**
     * 체결 가격으로 주식 현재가, 최고가, 최저가를 갱신합니다.
     * SRP: MatchTradeService에 있던 주식 가격 갱신 책임을 위임받음
     */
    @Transactional
    public Stock updateCurrentPrice(String stockCode, long tradePrice) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException("주식 코드를 찾을 수 없습니다: " + stockCode));

        stock.setCurrentPrice(tradePrice);
        if (stock.getHighPrice() == null || tradePrice > stock.getHighPrice()) {
            stock.setHighPrice(tradePrice);
        }
        if (stock.getLowPrice() == null || tradePrice < stock.getLowPrice()) {
            stock.setLowPrice(tradePrice);
        }
        return stockRepository.save(stock);
    }

    @Transactional
    public void closeMarket() {
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            Long finalPrice = redisService.getStockPrice(stock.getStockCode());
            stock.setClosingPrice(finalPrice);
            stock.setCurrentPrice(finalPrice);
        }
    }
}
