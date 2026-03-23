package com.stock.stockSimulator.dto.request;

import lombok.Data;

/**
 * 주문 취소 요청 DTO
 * SRP: Controller에서 정의하던 Inner class를 분리
 */
@Data
public class CancelOrderRequest {
    private Long memberId;
}
