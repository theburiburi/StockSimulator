package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.StockOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<StockOrder, Long> {

    // 매수 주문 시: 나보다 싸게 팔려는 사람들을 가격 낮은 순(ASC), 먼저 올린 순(ASC)으로 조회
//    @Query("SELECT o FROM StockOrder o WHERE o.stockCode = :code AND o.side = 'SELL' " +
//            "AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.PENDING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL) " +
//            "AND o.price <= :price ORDER BY o.price ASC, o.createdAt ASC")
//    List<StockOrder> findMatchingSells(@Param("code") String code, @Param("price") Long price);
//
//    // 매도 주문 시: 나보다 비싸게 사려는 사람들을 가격 높은 순(DESC), 먼저 올린 순(ASC)으로 조회
//    @Query("SELECT o FROM StockOrder o WHERE o.stockCode = :code AND o.side = 'BUY' " +
//            "AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.PENDING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL) " +
//            "AND o.price >= :price ORDER BY o.price DESC, o.createdAt ASC")
//    List<StockOrder> findMatchingBuys(@Param("code") String code, @Param("price") Long price);
}
