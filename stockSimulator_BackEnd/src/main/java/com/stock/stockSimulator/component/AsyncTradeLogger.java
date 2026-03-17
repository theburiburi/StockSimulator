package com.stock.stockSimulator.component;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.TradeLog;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
import com.stock.stockSimulator.repository.TradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTradeLogger {

    private final TradeRepository tradeRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final MemberStockRepository memberStockRepository;

    @Async("taskExecutor")
    @Transactional
    public void recordTrade(Long buyerId, Long sellerId, Long orderId,
                            String stockCode, int price, int qty) {
        try {
            long totalAmount = (long) price * qty;

            TradeLog tradeLog = new TradeLog(buyerId, sellerId, stockCode, price, qty);
            tradeRepository.save(tradeLog);

            // ================= [ 매수자 처리 ] =================
            // 지원자님이 작성한 findByIdWithLock(Long id) 사용!
            Member buyer = memberRepository.findByIdWithLock(buyerId)
                    .orElseThrow(() -> new RuntimeException("매수자를 찾을 수 없습니다."));
            buyer.decreaseBalance(totalAmount);

            MemberStock buyerStock = memberStockRepository.findByMemberIdAndStockCodeWithLock(buyerId, stockCode)
                    .orElseGet(() -> new MemberStock(buyerId, stockCode, 0));
            buyerStock.addQuantity(qty);
            memberStockRepository.save(buyerStock);

            // ================= [ 매도자 처리 ] =================
            Member seller = memberRepository.findByIdWithLock(sellerId)
                    .orElseThrow(() -> new RuntimeException("매도자를 찾을 수 없습니다."));
            seller.increaseBalance(totalAmount);

            MemberStock sellerStock = memberStockRepository.findByMemberIdAndStockCodeWithLock(sellerId, stockCode)
                    .orElseThrow(() -> new RuntimeException("매도자의 보유 주식이 없습니다."));
            sellerStock.removeQuantity(qty);

            // ================= [ 주문 상태 업데이트 ] =================
            StockOrder order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("주문 원장을 찾을 수 없습니다."));
            order.applyTrade(qty);

            log.info("[DB 비동기 기록 완료] 주문번호: {}", orderId);

        } catch (Exception e) {
            log.error("[DB 비동기 청산 실패] 주문번호: {}, 원인: {}", orderId, e.getMessage());
            throw e;
        }
    }
}
