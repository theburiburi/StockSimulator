package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 매칭 엔진
 * SRP: 주문 생성·검증·매칭·자산 이전만 담당.
 *      주식 가격 갱신/방송은 StockService에 위임.
 */
@Service
@RequiredArgsConstructor
public class MatchTradeService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final RedisService redisService;
    private final StockService stockService;
    private final SimpMessageSendingOperations messageTemplate;

    @Transactional
    public void placeMatchOrder(Long memberId, String code, OrderType orderType, Long price, Integer qty, OrderSide side) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));

        long totalAmount = calculateTotalAmount(code, orderType, side, price, qty);
        qty = adjustQtyForMarketOrder(code, orderType, side, qty);

        validateBalance(member, memberId, code, side, orderType, totalAmount, qty);

        StockOrder newOrder = buildOrder(member, code, orderType, side, price, qty);
        orderRepository.save(newOrder);

        executeMatching(newOrder);
    }

    @Transactional
    public void cancelMatchOrder(Long memberId, Long orderId) {
        StockOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("주문을 찾을 수 없습니다."));

        if (!order.getMember().getId().equals(memberId)) {
            throw new BusinessException("본인의 주문만 취소할 수 있습니다.");
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("취소할 수 없는 상태(완료 또는 이미 취소)입니다.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    // ────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────

    /**
     * 시장가/지정가에 따른 총 체결 예상 금액을 계산합니다.
     */
    private long calculateTotalAmount(String code, OrderType orderType, OrderSide side, long price, int qty) {
        if (orderType == OrderType.LIMIT) {
            return price * qty;
        }
        // 시장가: 반대편 호가 기준으로 총 금액 계산
        if (side == OrderSide.BUY) {
            List<StockOrder> sells = orderRepository.findMatchingSells(code, Long.MAX_VALUE);
            long total = 0L;
            int remaining = qty;
            for (StockOrder opp : sells) {
                int tradeQty = Math.min(remaining, opp.getRemainingQuantity());
                total += (long) tradeQty * opp.getPrice();
                remaining -= tradeQty;
                if (remaining == 0) break;
            }
            return total;
        }
        return 0L;
    }

    /**
     * 시장가 주문에서 실제 체결 가능 수량으로 조정합니다.
     */
    private int adjustQtyForMarketOrder(String code, OrderType orderType, OrderSide side, int qty) {
        if (orderType != OrderType.MARKET) return qty;

        if (side == OrderSide.SELL) {
            List<StockOrder> buys = orderRepository.findMatchingBuys(code, 0L);
            int available = buys.stream().mapToInt(StockOrder::getRemainingQuantity).sum();
            if (available == 0) throw new BusinessException("현재 매수 대기 물량이 없어 시장가 매도가 불가능합니다.");
            return Math.min(qty, available);
        }
        return qty;
    }

    /**
     * 잔고/보유 주식 수량 검증
     */
    private void validateBalance(Member member, Long memberId, String code, OrderSide side,
                                  OrderType orderType, long totalAmount, int qty) {
        if (side == OrderSide.BUY) {
            Long waitingAmount = orderRepository.findWaitingBuyAmount(memberId);
            long totalRequired = totalAmount + (waitingAmount != null ? waitingAmount : 0L);
            if (member.getBalance() < totalRequired) {
                throw new BusinessException("매수 주문 금액이 보유 잔고를 초과합니다. (진행 중인 대기 주문 포함)");
            }
        } else if (side == OrderSide.SELL && orderType == OrderType.LIMIT) {
            MemberStock stock = memberStockRepository.findByMemberIdAndStockCode(memberId, code)
                    .orElseThrow(() -> new BusinessException("해당 주식을 보유하고 있지 않습니다."));
            Integer waitingQty = orderRepository.findWaitingSellQuantity(memberId, code);
            long totalRequiredQty = qty + (waitingQty != null ? waitingQty : 0);
            if (stock.getQuantity() < totalRequiredQty) {
                throw new BusinessException("매도 주문 수량이 보유 주식 수량을 초과합니다. (진행 중인 대기 주문 포함)");
            }
        }
    }

    private StockOrder buildOrder(Member member, String code, OrderType orderType,
                                   OrderSide side, long price, int qty) {
        StockOrder order = new StockOrder();
        order.setMember(member);
        order.setStockCode(code);
        order.setOrderType(orderType != null ? orderType : OrderType.LIMIT);
        order.setSide(side);
        order.setPrice(price);
        order.setQuantity(qty);
        order.setRemainingQuantity(qty);
        order.setStatus(OrderStatus.WAITING);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private void executeMatching(StockOrder newOrder) {
        long searchPrice = (newOrder.getOrderType() == OrderType.MARKET)
                ? ((newOrder.getSide() == OrderSide.BUY) ? Long.MAX_VALUE : 0L)
                : newOrder.getPrice();

        List<StockOrder> opposites = (newOrder.getSide() == OrderSide.BUY)
                ? orderRepository.findMatchingSells(newOrder.getStockCode(), searchPrice)
                : orderRepository.findMatchingBuys(newOrder.getStockCode(), searchPrice);

        for (StockOrder oppOrder : opposites) {
            if (newOrder.getRemainingQuantity() <= 0) break;

            int tradeQty = Math.min(newOrder.getRemainingQuantity(), oppOrder.getRemainingQuantity());
            long tradePrice = oppOrder.getPrice();

            processAssetTransfer(newOrder, oppOrder, tradeQty, tradePrice);
            updateOrderProgress(newOrder, tradeQty);
            updateOrderProgress(oppOrder, tradeQty);

            // Redis 현재가 갱신
            redisService.setStockPrice(newOrder.getStockCode(), tradePrice);

            // 주식 가격 갱신 + WebSocket 방송 → StockService에 위임 (SRP)
            Stock updatedStock = stockService.updateCurrentPrice(newOrder.getStockCode(), tradePrice);
            messageTemplate.convertAndSend("/topic/stock", updatedStock);
        }

        if (newOrder.getOrderType() == OrderType.MARKET && newOrder.getRemainingQuantity() > 0) {
            newOrder.setStatus(OrderStatus.CANCELLED);
        }
    }

    private void processAssetTransfer(StockOrder newOrder, StockOrder oppOrder, int qty, long price) {
        Member m1 = newOrder.getMember();
        Member m2 = oppOrder.getMember();

        // 데드락 방지: ID 오름차순으로 락 획득
        Member firstMember = m1.getId() < m2.getId() ? m1 : m2;
        Member secondMember = m1.getId() < m2.getId() ? m2 : m1;

        Member lockedFirst  = memberRepository.findByIdWithLock(firstMember.getId()).get();
        Member lockedSecond = memberRepository.findByIdWithLock(secondMember.getId()).get();

        long totalAmount = (long) price * qty;

        Member buyer  = lockedFirst.getId().equals(newOrder.getMember().getId()) == (newOrder.getSide() == OrderSide.BUY)
                ? lockedFirst : lockedSecond;
        Member seller = buyer.getId().equals(lockedFirst.getId()) ? lockedSecond : lockedFirst;

        buyer.setBalance(buyer.getBalance() - totalAmount);
        seller.setBalance(seller.getBalance() + totalAmount);

        // 포트폴리오 갱신도 ID 오름차순 순서 유지
        if (m1.getId() < m2.getId()) {
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
            stock.updatePosition(price, qty);
        } else {
            stock.setQuantity(stock.getQuantity() - qty);
        }
        memberStockRepository.save(stock);
    }
}