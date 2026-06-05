package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTradeProcessor {

    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository orderRepository;
    private final RedisService redisService;
    private final StockService stockService;
    private final SimpMessageSendingOperations messageTemplate;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    private static record TradeResult(
        Long buyerId,
        Long sellerId,
        String stockCode,
        Long tradePrice,
        int tradeQty,
        Stock updatedStock
    ) {}

    /**
     * 매칭된 거래 목록을 백그라운드 스레드에서 RDB에 안전하게 동기화하고 알림을 보냅니다.
     * 실시간 체결과 자산 이전은 Redis Lua에서 원자적으로 끝나며, RDBMS는 확정된 체결 결과를 원자 UPDATE로 따라갑니다.
     */
    @Async("taskExecutor")
    public void processMatchedTrades(List<String> matchedTrades) {
        if (matchedTrades == null || matchedTrades.isEmpty()) {
            return;
        }

        log.info("⚡ [AsyncTradeProcessor] Processing {} matched trades in the background...", matchedTrades.size());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (String tradeStr : matchedTrades) {
            try {
                // 1. RDBMS 업데이트 (순수 DB 트랜잭션 실행 - 외부 네트워크 I/O 제거하여 DB 커넥션 점유 시간 최소화)
                TradeResult result = transactionTemplate.execute(status -> {
                    // 포맷: "buyOrderId:sellOrderId:buyerId:sellerId:price:tradeQty"
                    String[] parts = tradeStr.split(":");
                    Long sellOrderId = Long.parseLong(parts[1]);
                    Long buyerId = Long.parseLong(parts[2]);
                    Long sellerId = Long.parseLong(parts[3]);
                    Long tradePrice = Long.parseLong(parts[4]);
                    int tradeQty = Integer.parseInt(parts[5]);

                    StockOrder sellOrder = orderRepository.findById(sellOrderId)
                            .orElseThrow(() -> new BusinessException("상대 매도 주문을 찾을 수 없습니다."));
                    String stockCode = sellOrder.getStockCode();

                    long totalAmount = tradePrice * tradeQty;
                    memberRepository.addBalance(buyerId, -totalAmount);
                    memberRepository.addBalance(sellerId, totalAmount);
                    memberStockRepository.addBuyPosition(buyerId, stockCode, tradeQty, tradePrice);
                    memberStockRepository.subtractQuantity(sellerId, stockCode, tradeQty);
                    orderRepository.applyTrade(sellOrderId, tradeQty);

                    Stock updatedStock = stockService.updateCurrentPrice(stockCode, tradePrice);

                    return new TradeResult(buyerId, sellerId, stockCode, tradePrice, tradeQty, updatedStock);
                });

                // 2. 트랜잭션이 성공적으로 커밋되고 DB 커넥션이 반환 완료된 후 외부 I/O 비동기 실행
                if (result != null) {
                    // (1) Redis 현재가 갱신
                    redisService.setStockPrice(result.stockCode(), result.tradePrice());

                    // (2) WebSocket 실시간 전광판 송출
                    messageTemplate.convertAndSend("/topic/stock", result.updatedStock());

                    // (3) 개인 알림 전송 (매수자 / 매도자)
                    String formattedPrice = NumberFormat.getNumberInstance(Locale.KOREA).format(result.tradePrice());
                    notificationService.send(
                            result.buyerId(),
                            result.stockCode() + " " + result.tradeQty() + "주 매수 체결 완료 (" + formattedPrice + "원)",
                            NotificationType.TRADE_EXECUTED
                    );
                    notificationService.send(
                            result.sellerId(),
                            result.stockCode() + " " + result.tradeQty() + "주 매도 체결 완료 (" + formattedPrice + "원)",
                            NotificationType.TRADE_EXECUTED
                    );
                }
            } catch (Exception e) {
                log.error("❌ Failed to process matched trade asynchronously: " + tradeStr, e);
            }
        }
    }

}
