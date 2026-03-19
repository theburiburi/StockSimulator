package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.StockOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<StockOrder, Long> {

    @Query("SELECT o FROM StockOrder o WHERE o.stockCode = :code AND o.side = 'SELL' " +
            "AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.WAITING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL) " +
            "AND o.price <= :price ORDER BY o.price ASC, o.createdAt ASC")
    List<StockOrder> findMatchingSells(@Param("code") String code, @Param("price") Long price);

    @Query("SELECT o FROM StockOrder o WHERE o.stockCode = :code AND o.side = 'BUY' " +
            "AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.WAITING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL) " +
            "AND o.price >= :price ORDER BY o.price DESC, o.createdAt ASC")
    List<StockOrder> findMatchingBuys(@Param("code") String code, @Param("price") Long price);
}
