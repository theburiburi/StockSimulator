package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trade_log_redis_event_id",
                columnNames = "redis_event_id"
        )
)
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long buyerId;
    private Long sellerId;
    private Long buyOrderId;
    private Long sellOrderId;
    private String stockCode;
    private int price;
    private int quantity;

    @Column(name = "redis_event_id", unique = true)
    private String redisEventId;

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

    public TradeLog(String redisEventId, Long buyOrderId, Long sellOrderId,
                    Long buyerId, Long sellerId, String stockCode, int price, int quantity) {
        this.redisEventId = redisEventId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.stockCode = stockCode;
        this.price = price;
        this.quantity = quantity;
    }
}
