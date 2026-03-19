package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/orders")
@RequiredArgsConstructor
public class TradeController {
    private final TradeService tradeService;

    @PostMapping("/market-order")
    public String executeMarketOrder(@RequestBody OrderRequest request) {
        return tradeService.executeMarketOrder(
            request.getStockCode(), 
            request.getPrice(), 
            request.getQty(), 
            request.getBuyerId()
        );
    }

    @lombok.Data
    public static class OrderRequest {
        private String stockCode;
        private int price;
        private int qty;
        private String buyerId;
    }
}
