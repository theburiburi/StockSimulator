package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<TradeLog, Long> {
}
