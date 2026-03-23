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

    @Query("SELECT new com.stock.stockSimulator.dto.OrderDepthDto(o.side, o.price, SUM(o.remainingQuantity)) " +
           "FROM StockOrder o WHERE o.stockCode = :code " +
           "AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.WAITING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL) " +
           "GROUP BY o.side, o.price ORDER BY o.price DESC")
    List<com.stock.stockSimulator.dto.OrderDepthDto> findOrderDepthByStockCode(@Param("code") String code);

    @Query("SELECT SUM(o.remainingQuantity) FROM StockOrder o WHERE o.member.id = :memberId AND o.stockCode = :code AND o.side = 'SELL' AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.WAITING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL)")
    Integer findWaitingSellQuantity(@Param("memberId") Long memberId, @Param("code") String code);

    @Query("SELECT SUM(o.price * o.remainingQuantity) FROM StockOrder o WHERE o.member.id = :memberId AND o.side = 'BUY' AND o.status IN (com.stock.stockSimulator.domain.OrderStatus.WAITING, com.stock.stockSimulator.domain.OrderStatus.PARTIAL)")
    Long findWaitingBuyAmount(@Param("memberId") Long memberId);

    List<StockOrder> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT o FROM StockOrder o WHERE o.status IN " +
           "(com.stock.stockSimulator.domain.OrderStatus.WAITING, " +
           "com.stock.stockSimulator.domain.OrderStatus.PARTIAL)")
    List<StockOrder> findPendingOrders();
}
