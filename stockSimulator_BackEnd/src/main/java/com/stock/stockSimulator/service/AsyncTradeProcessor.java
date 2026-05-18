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
     * ID 순 정렬 비관적 락으로 데드락을 원천 차단합니다.
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
                    Long buyOrderId = Long.parseLong(parts[0]);
                    Long sellOrderId = Long.parseLong(parts[1]);
                    Long buyerId = Long.parseLong(parts[2]);
                    Long sellerId = Long.parseLong(parts[3]);
                    Long tradePrice = Long.parseLong(parts[4]);
                    int tradeQty = Integer.parseInt(parts[5]);

                    // 1. 데드락 방지: ID 오름차순으로 DB 비관적 락 획득
                    Long firstMemberId = buyerId < sellerId ? buyerId : sellerId;
                    Long secondMemberId = buyerId < sellerId ? sellerId : buyerId;

                    Member lockedFirst = memberRepository.findByIdWithLock(firstMemberId)
                            .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
                    Member lockedSecond = memberRepository.findByIdWithLock(secondMemberId)
                            .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));

                    Member buyer = lockedFirst.getId().equals(buyerId) ? lockedFirst : lockedSecond;
                    Member seller = buyer.getId().equals(lockedFirst.getId()) ? lockedSecond : lockedFirst;

                    // RDBMS 잔고 이체
                    long totalAmount = tradePrice * tradeQty;
                    buyer.setBalance(buyer.getBalance() - totalAmount);
                    seller.setBalance(seller.getBalance() + totalAmount);

                    memberRepository.save(buyer);
                    memberRepository.save(seller);

                    // 2. 보유주식 갱신도 ID 오름차순 순서로 락 획득
                    StockOrder oppOrder = orderRepository.findById(sellOrderId)
                            .orElseThrow(() -> new BusinessException("상대 매도 주문을 찾을 수 없습니다."));
                    
                    String stockCode = oppOrder.getStockCode();

                    if (buyerId < sellerId) {
                        updateStockPortfolio(buyer, stockCode, tradeQty, tradePrice, true);
                        updateStockPortfolio(seller, stockCode, tradeQty, tradePrice, false);
                    } else {
                        updateStockPortfolio(seller, stockCode, tradeQty, tradePrice, false);
                        updateStockPortfolio(buyer, stockCode, tradeQty, tradePrice, true);
                    }

                    // 3. 매칭 상대방 주문의 데이터베이스 수량 차감 및 상태 갱신
                    updateOrderProgress(oppOrder, tradeQty);
                    orderRepository.save(oppOrder);

                    // 4. DB 현재가 갱신 및 정보 저장
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

    private void updateOrderProgress(StockOrder order, int qty) {
        order.setRemainingQuantity(order.getRemainingQuantity() - qty);
        order.setStatus(order.getRemainingQuantity() == 0 ? OrderStatus.COMPLETED : OrderStatus.PARTIAL);
    }

    private void updateStockPortfolio(Member member, String code, int qty, long price, boolean isBuy) {
        MemberStock stock = memberStockRepository.findByMemberIdAndStockCodeWithLock(member.getId(), code)
                .orElseGet(() -> {
                    MemberStock ns = new MemberStock();
                    ns.setMemberId(member.getId());
                    ns.setStockCode(code);
                    ns.setQuantity(0);
                    ns.setAveragePrice(0L);
                    return ns;
                });

        if (isBuy) {
            stock.updatePosition(price, qty);
        } else {
            stock.setQuantity(stock.getQuantity() - qty);
        }
        memberStockRepository.save(stock);
    }
}
