package com.stock.stockSimulator.service;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.domain.OrderStatus;
import com.stock.stockSimulator.domain.OrderSide;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchTradeService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final RedisService redisService;

    @Transactional
    public void placeMatchOrder(Long memberId, String code, Long price, Integer qty, OrderSide side) {
        // 1. 주문자 조회 (Pessimistic Lock으로 잔고 보호)
        Member member = memberRepository.findByIdWithLock(memberId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 2. 주문 엔티티 생성 및 DB 저장 (호가창 진입)
        StockOrder newOrder = new StockOrder();
        newOrder.setMember(member);
        newOrder.setStockCode(code);
        newOrder.setSide(side);
        newOrder.setPrice(price);
        newOrder.setQuantity(qty);
        newOrder.setRemainingQuantity(qty);
        newOrder.setStatus(OrderStatus.WAITING);
        newOrder.setCreatedAt(LocalDateTime.now());
        orderRepository.save(newOrder);

        // 3. 매칭 엔진 가동
        executeMatching(newOrder);
    }

    private void executeMatching(StockOrder newOrder) {
        // 반대편 물량 조회 (가격/시간 우선순위 쿼리)
        List<StockOrder> opposites = (newOrder.getSide() == OrderSide.BUY)
                ? orderRepository.findMatchingSells(newOrder.getStockCode(), newOrder.getPrice())
                : orderRepository.findMatchingBuys(newOrder.getStockCode(), newOrder.getPrice());

        for (StockOrder oppOrder : opposites) {
            if (newOrder.getRemainingQuantity() <= 0) break;

            int tradeQty = Math.min(newOrder.getRemainingQuantity(), oppOrder.getRemainingQuantity());
            long tradePrice = oppOrder.getPrice(); // 기존 호가 가격 기준

            // 4. 자산 이동 처리 (DB 반영)
            processAssetTransfer(newOrder, oppOrder, tradeQty, tradePrice);

            // 5. 주문 상태 업데이트
            updateOrderProgress(newOrder, tradeQty);
            updateOrderProgress(oppOrder, tradeQty);

            // 6. Redis 현재가 갱신 (전광판 반영)
            redisService.setStockPrice(newOrder.getStockCode(), tradePrice);
        }
    }

    private void processAssetTransfer(StockOrder newOrder, StockOrder oppOrder, int qty, long price) {
        Member buyer = (newOrder.getSide() == OrderSide.BUY) ? newOrder.getMember() : oppOrder.getMember();
        Member seller = (newOrder.getSide() == OrderSide.SELL) ? newOrder.getMember() : oppOrder.getMember();

        long totalAmount = (long) price * qty;

        // 잔고 이동
        buyer.setBalance(buyer.getBalance() - totalAmount);
        seller.setBalance(seller.getBalance() + totalAmount);

        // 주식 및 평단가 업데이트
        updateStockPortfolio(buyer, newOrder.getStockCode(), qty, price, true);
        updateStockPortfolio(seller, newOrder.getStockCode(), qty, price, false);
    }

    private void updateOrderProgress(StockOrder order, int qty) {
        order.setRemainingQuantity(order.getRemainingQuantity() - qty);
        order.setStatus(order.getRemainingQuantity() == 0 ? OrderStatus.COMPLETED : OrderStatus.PARTIAL);
    }

    private void updateStockPortfolio(Member member, String code, int qty, long price, boolean isBuy) {
        MemberStock stock = memberStockRepository.findByMemberIdAndStockCode(member.getId(), code)
                .orElseGet(() -> {
                    MemberStock ns = new MemberStock();
                    ns.setMemberId(member.getId());
                    ns.setStockCode(code);
                    ns.setQuantity(0);
                    ns.setAveragePrice(0L);
                    return ns;
                });

        if (isBuy) {
            stock.updatePosition(price, qty); // 가중 평균 평단가 계산
        } else {
            stock.setQuantity(stock.getQuantity() - qty);
        }
        memberStockRepository.save(stock);
    }
}