package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.OrderType;
import com.stock.stockSimulator.dto.OrderDepthDto;
import com.stock.stockSimulator.dto.request.CancelOrderRequest;
import com.stock.stockSimulator.dto.request.TradeRequest;
import com.stock.stockSimulator.service.MatchTradeService;
import com.stock.stockSimulator.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRP: 주문 관련 HTTP 처리만 담당
 * Inner DTO 클래스를 dto/request 패키지로 분리
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradeController {

    private final MatchTradeService matchTradeService;
    private final OrderRepository orderRepository;

    @PostMapping("/trade")
    public ResponseEntity<String> placeTradeOrder(@RequestBody TradeRequest request) {
        if (request.getOrderType() == OrderType.LIMIT && request.getPrice() % 100 != 0) {
            return ResponseEntity.badRequest().body("가격은 100원 단위로만 입력 가능합니다.");
        }
        matchTradeService.placeMatchOrder(
            request.getMemberId(),
            request.getStockCode(),
            request.getOrderType() != null ? request.getOrderType() : OrderType.LIMIT,
            request.getPrice(),
            request.getQty(),
            request.getSide()
        );
        return ResponseEntity.ok("Order placed successfully");
    }

    @GetMapping("/depth/{code}")
    public List<OrderDepthDto> getOrderDepth(@PathVariable("code") String code) {
        return orderRepository.findOrderDepthByStockCode(code);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<String> cancelTradeOrder(@PathVariable("orderId") Long orderId, @RequestBody CancelOrderRequest request) {
        try {
            matchTradeService.cancelMatchOrder(request.getMemberId(), orderId);
            return ResponseEntity.ok("주문이 성공적으로 취소되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
