package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class StockOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private String stockCode;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderType orderType = OrderType.LIMIT;

    private Long price;
    private Integer quantity;
    private Integer remainingQuantity;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public void applyTrade(int quantity){
        this.remainingQuantity = Math.max(0, this.remainingQuantity - quantity);
        this.status = this.remainingQuantity == 0 ? OrderStatus.COMPLETED : OrderStatus.PARTIAL;
    }
}