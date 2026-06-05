package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTradeProcessor {

    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository orderRepository;
    private final RedisService redisService;
    private final StockService stockService;
    private final TradeRepository tradeRepository;
    private final SimpMessageSendingOperations messageTemplate;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    private final StringRedisTemplate redisTemplate;

    private static final String TRADE_EVENT_STREAM_KEY = "trade:events";

    private static record TradeResult(
        Long buyerId,
        Long sellerId,
        String stockCode,
        Long tradePrice,
        int tradeQty,
        Stock updatedStock,
        boolean alreadyProcessed
    ) {}

    private static record TradeEvent(
            String redisEventId,
            Long buyOrderId,
            Long sellOrderId,
            Long buyerId,
            Long sellerId,
            Long tradePrice,
            int tradeQty
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
                TradeEvent event = parseTradeEvent(tradeStr);

                // 1. RDBMS 업데이트 (순수 DB 트랜잭션 실행 - 외부 네트워크 I/O 제거하여 DB 커넥션 점유 시간 최소화)
                TradeResult result = transactionTemplate.execute(status -> {
                    // 포맷: "buyOrderId:sellOrderId:buyerId:sellerId:price:tradeQty"
                    if (event.redisEventId() != null && tradeRepository.existsByRedisEventId(event.redisEventId())) {
                        return new TradeResult(null, null, null, null, 0, null, true);
                    }

                    StockOrder sellOrder = orderRepository.findById(event.sellOrderId())
                            .orElseThrow(() -> new BusinessException("상대 매도 주문을 찾을 수 없습니다."));
                    String stockCode = sellOrder.getStockCode();

                    long totalAmount = event.tradePrice() * event.tradeQty();
                    memberRepository.addBalance(event.buyerId(), -totalAmount);
                    memberRepository.addBalance(event.sellerId(), totalAmount);
                    memberStockRepository.addBuyPosition(event.buyerId(), stockCode, event.tradeQty(), event.tradePrice());
                    memberStockRepository.subtractQuantity(event.sellerId(), stockCode, event.tradeQty());
                    orderRepository.applyTrade(event.sellOrderId(), event.tradeQty());
                    tradeRepository.save(new TradeLog(
                            event.redisEventId(),
                            event.buyOrderId(),
                            event.sellOrderId(),
                            event.buyerId(),
                            event.sellerId(),
                            stockCode,
                            Math.toIntExact(event.tradePrice()),
                            event.tradeQty()
                    ));

                    Stock updatedStock = stockService.updateCurrentPrice(stockCode, event.tradePrice());

                    return new TradeResult(event.buyerId(), event.sellerId(), stockCode, event.tradePrice(), event.tradeQty(), updatedStock, false);
                });

                // 2. 트랜잭션이 성공적으로 커밋되고 DB 커넥션이 반환 완료된 후 외부 I/O 비동기 실행
                if (result != null) {
                    acknowledgeTradeEvent(event.redisEventId());
                    if (result.alreadyProcessed()) {
                        continue;
                    }

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

    public void replayPendingTradeEvents() {
        List<String> pendingEvents = readPendingTradeEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.warn("🔁 [AsyncTradeProcessor] Replaying {} pending Redis trade events before orderbook rebuild...", pendingEvents.size());
        processMatchedTrades(pendingEvents);
    }

    private List<String> readPendingTradeEvents() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(TRADE_EVENT_STREAM_KEY, Range.unbounded());
        List<String> events = new ArrayList<>();
        if (records == null) {
            return events;
        }

        for (MapRecord<String, Object, Object> record : records) {
            Map<Object, Object> value = record.getValue();
            Object trade = value.get("trade");
            if (trade != null) {
                events.add(record.getId().getValue() + "|" + trade);
            }
        }
        return events;
    }

    private TradeEvent parseTradeEvent(String rawEvent) {
        String redisEventId = null;
        String trade = rawEvent;
        int separator = rawEvent.indexOf('|');
        if (separator >= 0) {
            redisEventId = rawEvent.substring(0, separator);
            trade = rawEvent.substring(separator + 1);
        }

        String[] parts = trade.split(":");
        if (parts.length != 6) {
            throw new BusinessException("체결 이벤트 포맷이 올바르지 않습니다: " + rawEvent);
        }

        return new TradeEvent(
                redisEventId,
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3]),
                Long.parseLong(parts[4]),
                Integer.parseInt(parts[5])
        );
    }

    private void acknowledgeTradeEvent(String redisEventId) {
        if (redisEventId != null) {
            redisTemplate.opsForStream().delete(TRADE_EVENT_STREAM_KEY, redisEventId);
        }
    }

}
