package com.stock.stockSimulator.dto;

import com.stock.stockSimulator.domain.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDepthDto {
    private OrderSide side;
    private Long price;
    private Long totalQuantity; // SUM(remainingQuantity) returns Long
}
