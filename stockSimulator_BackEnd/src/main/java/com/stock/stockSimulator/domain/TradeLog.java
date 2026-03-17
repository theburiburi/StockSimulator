package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long buyerId;
    private Long sellerId;
    private String stockCode;
    private int price;
    private int quantity;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime tradedAt;

    public TradeLog(Long buyerId, Long sellerId, String stockCode, int price, int quantity){
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.stockCode = stockCode;
        this.price = price;
        this.quantity = quantity;
    }
}
