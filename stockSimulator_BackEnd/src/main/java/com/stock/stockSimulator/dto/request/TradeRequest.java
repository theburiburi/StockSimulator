package com.stock.stockSimulator.dto.request;

import com.stock.stockSimulator.domain.OrderSide;
import com.stock.stockSimulator.domain.OrderType;
import lombok.Data;

/**
 * 주문 요청 DTO
 * SRP: Controller에서 정의하던 Inner class를 분리
 */
@Data
public class TradeRequest {
    private Long memberId;
    private String stockCode;
    private OrderType orderType;
    private Long price;
    private Integer qty;
    private OrderSide side;
}
