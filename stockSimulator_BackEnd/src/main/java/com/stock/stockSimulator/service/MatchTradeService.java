package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Redis Lua 스크립트 기반 고성능 주문 매칭 엔진
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchTradeService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final RedisService redisService;
    private final StockService stockService;
    private final SimpMessageSendingOperations messageTemplate;
    private final NotificationService notificationService;
    private final AsyncTradeProcessor asyncTradeProcessor;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> matchEngineScript;
    private final DefaultRedisScript<Long> cancelOrderScript;

    /**
     * 서버 시작 시 데이터베이스의 최신 상태(잔고, 보유주식, 대기주문)를 Redis로 완벽하게 이전합니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncAllToRedisOnStartup() {
        log.info("🚀 [TradeEngine] Syncing all database members and pending orders to Redis...");

        try {
            // 기존 Redis 데이터 초기화
            redisTemplate.delete(redisTemplate.keys("member:*"));
            redisTemplate.delete(redisTemplate.keys("orderbook:*"));
            redisTemplate.delete(redisTemplate.keys("orders:*"));
        } catch (Exception e) {
            log.warn("⚠️ No pre-existing redis keys or redis unavailable for clearing: {}", e.getMessage());
        }

        // 1. 회원 자산 동기화
        List<Member> members = memberRepository.findAll();
        for (Member member : members) {
            Long memberId = member.getId();
            
            // 사용 가능 잔액 계산 (전체 잔액 - 진행 중인 대기 매수금액)
            Long waitingAmount = orderRepository.findWaitingBuyAmount(memberId);
            long availableBalance = member.getBalance() - (waitingAmount != null ? waitingAmount : 0L);
            redisTemplate.opsForValue().set("member:" + memberId + ":balance", String.valueOf(availableBalance));

            // 보유 주식 동기화
            List<MemberStock> stocks = memberStockRepository.findAllByMemberId(memberId);
            for (MemberStock ms : stocks) {
                Integer waitingQty = orderRepository.findWaitingSellQuantity(memberId, ms.getStockCode());
                int availableQty = ms.getQuantity() - (waitingQty != null ? waitingQty : 0);
                redisTemplate.opsForValue().set("member:" + memberId + ":stock:" + ms.getStockCode(), String.valueOf(availableQty));
            }
        }

        // 2. 데이터베이스 내의 대기(WAITING/PARTIAL) 주문들을 Redis 호가창에 순차적으로 로드
        List<StockOrder> pendingOrders = orderRepository.findPendingOrders();
        for (StockOrder order : pendingOrders) {
            String side = order.getSide().name().toLowerCase();
            String code = order.getStockCode();
            long price = order.getPrice();
            String listKey = "orders:" + side + ":" + code + ":" + price;
            String bookKey = "orderbook:" + side + ":" + code;

            // 포맷: "orderId:memberId:remainingQuantity"
            String value = order.getId() + ":" + order.getMember().getId() + ":" + order.getRemainingQuantity();
            redisTemplate.opsForList().rightPush(listKey, value);
            redisTemplate.opsForZSet().add(bookKey, String.valueOf(price), price);
        }

        log.info("✅ [TradeEngine] Synchronized {} members and {} pending orders to Redis successfully!", members.size(), pendingOrders.size());
    }

    public void placeMatchOrder(Long memberId, String code, OrderType orderType, Long price, Integer qty, OrderSide side) {
        // 1. 회원 및 자산 Redis 동기화 검증 (Lazy load)
        syncMemberToRedis(memberId);
        if (side == OrderSide.SELL) {
            syncMemberStockToRedis(memberId, code);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));

        // 2. 가벼운 DB INSERT를 통해 Order ID 선발급 (락 경합 없음)
        StockOrder newOrder = buildOrder(member, code, orderType, side, price, qty);
        orderRepository.saveAndFlush(newOrder);

        // 3. Redis Lua 매칭 스크립트 실행
        List<Object> luaResult;
        try {
            luaResult = redisTemplate.execute(
                    matchEngineScript,
                    List.of(), // KEYS
                    String.valueOf(memberId),
                    code,
                    String.valueOf(newOrder.getId()),
                    orderType.name(),
                    side.name(),
                    String.valueOf(price),
                    String.valueOf(qty)
            );
        } catch (Exception e) {
            log.error("❌ Redis Lua Script Execution failed: ", e);
            throw new BusinessException("주문 처리 중 매칭 엔진 오류가 발생했습니다.");
        }

        if (luaResult == null || luaResult.isEmpty()) {
            throw new BusinessException("매칭 엔진으로부터 응답이 없습니다.");
        }

        // 예외 처리 (잔액 부족 / 주식 부족)
        Object firstEl = luaResult.get(0);
        if (firstEl instanceof Map) {
            Map<?, ?> errMap = (Map<?, ?>) firstEl;
            String err = (String) errMap.get("err");
            if ("INSUFFICIENT_BALANCE".equals(err)) {
                throw new BusinessException("매수 주문 금액이 보유 잔고를 초과합니다. (진행 중인 대기 주문 포함)");
            } else if ("INSUFFICIENT_STOCK".equals(err)) {
                throw new BusinessException("매도 주문 수량이 보유 주식 수량을 초과합니다. (진행 중인 대기 주문 포함)");
            }
        }

        // 매칭 결과 파싱
        String remainingQtyStr = (String) luaResult.get(0);
        int remainingQty = Integer.parseInt(remainingQtyStr);
        List<String> matchedTrades = (List<String>) luaResult.get(1);

        // 4. 발주한 주문의 데이터베이스 정보 갱신
        newOrder.setRemainingQuantity(remainingQty);
        if (remainingQty == 0) {
            newOrder.setStatus(OrderStatus.COMPLETED);
        } else if (remainingQty < qty) {
            newOrder.setStatus(OrderStatus.PARTIAL);
        } else {
            newOrder.setStatus(OrderStatus.WAITING);
        }

        if (orderType == OrderType.MARKET && remainingQty > 0) {
            newOrder.setStatus(OrderStatus.CANCELLED);
        }
        orderRepository.save(newOrder);

        // 5. 체결 완료된 거래에 대해서만 트랜잭션 락 걸고 DB 자산 이전 수행 (비동기 위임)
        asyncTradeProcessor.processMatchedTrades(matchedTrades);
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

        // Redis Lua 취소 스크립트 실행
        Long cancelledQty = redisTemplate.execute(
                cancelOrderScript,
                List.of(),
                String.valueOf(orderId),
                String.valueOf(memberId),
                order.getStockCode(),
                order.getSide().name(),
                String.valueOf(order.getPrice())
        );

        if (cancelledQty != null && cancelledQty > 0) {
            // Redis에서 안정적으로 취소 및 환불 완료 -> DB 정보 업데이트
            order.setStatus(OrderStatus.CANCELLED);
            order.setRemainingQuantity(order.getRemainingQuantity() - cancelledQty.intValue());
            orderRepository.save(order);
        } else {
            throw new BusinessException("이미 체결 완료되었거나 취소할 수 없는 주문입니다.");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Private Helpers
    // ────────────────────────────────────────────────────────────────

    private void syncMemberToRedis(Long memberId) {
        String balanceKey = "member:" + memberId + ":balance";
        if (Boolean.FALSE.equals(redisTemplate.hasKey(balanceKey))) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
            Long waitingAmount = orderRepository.findWaitingBuyAmount(memberId);
            long availableBalance = member.getBalance() - (waitingAmount != null ? waitingAmount : 0L);
            redisTemplate.opsForValue().set(balanceKey, String.valueOf(availableBalance));
        }
    }

    private void syncMemberStockToRedis(Long memberId, String stockCode) {
        String stockKey = "member:" + memberId + ":stock:" + stockCode;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            MemberStock stock = memberStockRepository.findByMemberIdAndStockCode(memberId, stockCode)
                    .orElse(null);
            int availableQty = 0;
            if (stock != null) {
                Integer waitingQty = orderRepository.findWaitingSellQuantity(memberId, stockCode);
                availableQty = stock.getQuantity() - (waitingQty != null ? waitingQty : 0);
            }
            redisTemplate.opsForValue().set(stockKey, String.valueOf(availableQty));
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
}