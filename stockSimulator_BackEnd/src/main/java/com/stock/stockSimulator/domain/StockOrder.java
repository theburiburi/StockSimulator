package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class StockOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long memberId;
    private String stockCode;
    private int price;
    private int quantity;
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public StockOrder(Long memberId, String stockCode, int price,
                      int quantity, int remainingQuantity, OrderStatus status){
        this.memberId = memberId;
        this.stockCode = stockCode;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
    }

    public void applyTrade(int quantity){
        this.remainingQuantity -= quantity;
        this.status = this.remainingQuantity <= 0 ? OrderStatus.COMPLETED : OrderStatus.PARTIAL;
    }
}