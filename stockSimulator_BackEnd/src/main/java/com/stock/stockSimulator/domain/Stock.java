package com.stock.stockSimulator.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private Long currentPrice;
    private Long openingPrice;
    private Long endingPrice;

    public Stock(String code, String name, Long currentPrice, Long openingPrice, Long endingPrice){
        this.code = code;
        this.name = name;
        this.currentPrice = currentPrice;
        this.openingPrice = openingPrice;
        this.endingPrice = endingPrice;
    }


    public double getChangeRate() {
        if (openingPrice == null || openingPrice == 0) return 0.0;
        return ((double) (currentPrice - openingPrice) / openingPrice) * 100;
    }
}
