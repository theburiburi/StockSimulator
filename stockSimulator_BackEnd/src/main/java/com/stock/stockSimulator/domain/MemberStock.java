package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_stock_member_code",
                columnNames = {"member_id", "stock_code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class MemberStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "stock_code")
    private String stockCode;

    private int quantity;

    @Column(name = "average_price")
    private Long averagePrice;

    public MemberStock(Long memberId, String stockCode, int quantity){
        this.memberId = memberId;
        this.stockCode = stockCode;
        this.quantity = quantity;
        this.averagePrice = 0L;
    }

    public void addQuantitiy(int amount){
        this.quantity += amount;
    }

    public void updatePosition(long price, int qty) {
        long totalValue = (this.averagePrice == null ? 0 : this.averagePrice) * this.quantity;
        long newTotalValue = totalValue + (price * qty);
        this.quantity += qty;
        this.averagePrice = this.quantity == 0 ? 0 : newTotalValue / this.quantity;
    }
}
