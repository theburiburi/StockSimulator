package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class MemberStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long memberId;
    private String stockCode;
    private int quantity;

    public MemberStock(Long memberId, String stockCode, int quantity){
        this.memberId = memberId;
        this.stockCode = stockCode;
        this.quantity = quantity;
    }

    public void addQuantitiy(int amount){
        this.quantity += amount;
    }
}
