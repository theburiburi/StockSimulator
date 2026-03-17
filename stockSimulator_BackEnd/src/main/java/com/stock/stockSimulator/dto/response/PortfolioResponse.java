package com.stock.stockSimulator.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PortfolioResponse {
    private String stockName;
    private Integer quantity;
    private Long averagePrice;
    private Long currentPrice;
    private Long valuation;
    private Double profitRate;
}
