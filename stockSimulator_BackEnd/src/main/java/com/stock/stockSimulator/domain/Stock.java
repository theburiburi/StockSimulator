package com.stock.stockSimulator.domain;

import jakarta.persistence.Column;
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
public class Stock {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String stockCode;

    @Column(name = "name")
    private String companyName;

    private Long openingPrice;

    @Column(name = "ending_price")
    private Long closingPrice;

    private Long highPrice;
    private Long lowPrice;
    private Long currentPrice;


    public Stock(String stockCode, String companyName){
        this.stockCode = stockCode;
        this.companyName = companyName;
    }

    //Redis 데이터 디비 엔터티로 반영 메서드
    public void updateClosingInfo(Long openingPrice, Long highPrice, Long lowPrice, Long closingPrice){
        this.openingPrice = openingPrice;
        this.closingPrice = closingPrice;
        this.currentPrice = closingPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
    }
}
