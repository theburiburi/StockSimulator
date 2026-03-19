package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Stock;
import com.stock.stockSimulator.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {
    
    private final StockRepository stockRepository;

    @GetMapping
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
}
