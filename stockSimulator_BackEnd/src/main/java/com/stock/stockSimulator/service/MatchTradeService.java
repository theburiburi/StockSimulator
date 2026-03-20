package com.stock.stockSimulator.service;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.domain.OrderStatus;
import com.stock.stockSimulator.domain.OrderSide;
import com.stock.stockSimulator.domain.OrderType;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
import com.stock.stockSimulator.repository.StockRepository;
import com.stock.stockSimulator.domain.Stock;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
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
    private final StockRepository stockRepository;
    private final SimpMessageSendingOperations messageTemplate;

    @Transactional
    public void placeMatchOrder(Long memberId, String code, OrderType orderType, Long price, Integer qty, OrderSide side) {
        // 1. 주문자 조회 (데이터 정합성 기본 체크)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        long totalAmount = 0L;
        if (orderType == OrderType.MARKET) {
            if (side == OrderSide.BUY) {
                List<StockOrder> sells = orderRepository.findMatchingSells(code, Long.MAX_VALUE);
                int remQty = qty;
                for (StockOrder opp : sells) {
                    int tradeQty = Math.min(remQty, opp.getRemainingQuantity());
                    totalAmount += (long) tradeQty * opp.getPrice();
                    remQty -= tradeQty;
                    if (remQty == 0) break;
                }
                if (remQty > 0) {
                    qty -= remQty;
                    if (qty == 0) throw new RuntimeException("현재 매도 대기 물량이 없어 시장가 매수가 불가능합니다.");
                }
                price = 0L;
            } else if (side == OrderSide.SELL) {
                memberStockRepository.findByMemberIdAndStockCode(memberId, code)
                        .orElseThrow(() -> new RuntimeException("해당 주식을 보유하고 있지 않습니다."));
                List<StockOrder> buys = orderRepository.findMatchingBuys(code, 0L);
                int availableQty = 0;
                for (StockOrder opp : buys) {
                    availableQty += opp.getRemainingQuantity();
                }
                if (qty > availableQty) {
                    qty = availableQty;
                    if (qty == 0) throw new RuntimeException("현재 매수 대기 물량이 없어 시장가 매도가 불가능합니다.");
                }
                price = 0L;
            }
        } else {
            totalAmount = price * qty;
        }

        if (side == OrderSide.BUY) {
            Long waitingAmount = orderRepository.findWaitingBuyAmount(memberId);
            long totalRequired = totalAmount + (waitingAmount != null ? waitingAmount : 0L);
            if (member.getBalance() < totalRequired) {
                throw new RuntimeException("매수 주문 금액이 보유 잔고를 초과합니다. (진행 중인 대기 주문 포함)");
            }
        } else if (side == OrderSide.SELL) {
            MemberStock stock = memberStockRepository.findByMemberIdAndStockCode(memberId, code)
                    .orElseThrow(() -> new RuntimeException("해당 주식을 보유하고 있지 않습니다."));
            Integer waitingQty = orderRepository.findWaitingSellQuantity(memberId, code);
            long totalRequiredQty = qty + (waitingQty != null ? waitingQty : 0);
            if (stock.getQuantity() < totalRequiredQty) {
                throw new RuntimeException("매도 주문 수량이 보유 주식 수량을 초과합니다. (진행 중인 대기 주문 포함)");
            }
        }

        // 2. 주문 엔티티 생성 및 DB 저장 (호가창 진입)
        StockOrder newOrder = new StockOrder();
        newOrder.setMember(member);
        newOrder.setStockCode(code);
        newOrder.setOrderType(orderType != null ? orderType : OrderType.LIMIT);
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

    @Transactional
    public void cancelMatchOrder(Long memberId, Long orderId) {
        StockOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
                
        if (!order.getMember().getId().equals(memberId)) {
            throw new RuntimeException("본인의 주문만 취소할 수 있습니다.");
        }
        
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("취소할 수 없는 상태(완료 또는 이미 취소)입니다.");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private void executeMatching(StockOrder newOrder) {
        long searchPrice = (newOrder.getOrderType() == OrderType.MARKET) 
                ? ((newOrder.getSide() == OrderSide.BUY) ? Long.MAX_VALUE : 0L) 
                : newOrder.getPrice();

        // 반대편 물량 조회 (가격/시간 우선순위 쿼리)
        List<StockOrder> opposites = (newOrder.getSide() == OrderSide.BUY)
                ? orderRepository.findMatchingSells(newOrder.getStockCode(), searchPrice)
                : orderRepository.findMatchingBuys(newOrder.getStockCode(), searchPrice);

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
            
            // 7. 실시간 전광판 갱신
            Stock stock = stockRepository.findByStockCode(newOrder.getStockCode()).orElse(null);
            if (stock != null) {
                stock.setCurrentPrice(tradePrice);
                
                // 최고/최저가 갱신 로직 추가
                if (stock.getHighPrice() == null || tradePrice > stock.getHighPrice()) {
                    stock.setHighPrice(tradePrice);
                }
                if (stock.getLowPrice() == null || tradePrice < stock.getLowPrice()) {
                    stock.setLowPrice(tradePrice);
                }
                
                stockRepository.save(stock);
                messageTemplate.convertAndSend("/topic/stock", stock);
            }
        }
        
        // Cancel the rest if it's a MARKET order
        if (newOrder.getOrderType() == OrderType.MARKET && newOrder.getRemainingQuantity() > 0) {
            newOrder.setStatus(OrderStatus.CANCELLED);
        }
    }

    private void processAssetTransfer(StockOrder newOrder, StockOrder oppOrder, int qty, long price) {
        Member m1 = newOrder.getMember();
        Member m2 = oppOrder.getMember();

        // 데드락 방지를 위한 일관된 락 획득 순서 (XOR 기반 Fairness 적용)
        long currentSeed = System.currentTimeMillis() / 60000;
        long p1 = m1.getId() ^ currentSeed;
        long p2 = m2.getId() ^ currentSeed;

        Member firstMember, secondMember;
        if (p1 < p2) {
            firstMember = m1;
            secondMember = m2;
        } else {
            firstMember = m2;
            secondMember = m1;
        }

        // 1. 멤버 락 획득 (잔고 보호)
        Member lockedFirst = memberRepository.findByIdWithLock(firstMember.getId()).get();
        Member lockedSecond = memberRepository.findByIdWithLock(secondMember.getId()).get();

        long totalAmount = (long) price * qty;
        
        // Locked 인스턴스에서 Buyer/Seller 다시 식별
        Member buyer = (newOrder.getSide() == OrderSide.BUY) ? 
                (lockedFirst.getId().equals(newOrder.getMember().getId()) ? lockedFirst : lockedSecond) :
                (lockedFirst.getId().equals(oppOrder.getMember().getId()) ? lockedFirst : lockedSecond);
        Member seller = (buyer.getId().equals(lockedFirst.getId())) ? lockedSecond : lockedFirst;

        // 잔고 이동
        buyer.setBalance(buyer.getBalance() - totalAmount);
        seller.setBalance(seller.getBalance() + totalAmount);

        // 2. 주식 포트폴리오 락 획득 및 업데이트 (동일한 XOR 순서 적용)
        if (p1 < p2) {
            updateStockPortfolio(m1, newOrder.getStockCode(), qty, price, newOrder.getSide() == OrderSide.BUY);
            updateStockPortfolio(m2, newOrder.getStockCode(), qty, price, oppOrder.getSide() == OrderSide.BUY);
        } else {
            updateStockPortfolio(m2, newOrder.getStockCode(), qty, price, oppOrder.getSide() == OrderSide.BUY);
            updateStockPortfolio(m1, newOrder.getStockCode(), qty, price, newOrder.getSide() == OrderSide.BUY);
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
            stock.updatePosition(price, qty); // 가중 평균 평단가 계산
        } else {
            stock.setQuantity(stock.getQuantity() - qty);
        }
        memberStockRepository.save(stock);
    }
}