package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByStockCode(String stockCode);
}
