package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SRP: 장 개장/마감 비즈니스 로직만 담당.
 *      권한 체크는 @PreAuthorize + AdminController에서 처리하므로 여기선 비즈니스 로직에만 집중.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String SYSTEM_BOT_EMAIL  = "system-bot@stock-trading.com";
    private static final String SYSTEM_BOT_NAME   = "System Liquidity Bot";
    private static final long   SYSTEM_BOT_BALANCE = 999_999_999_999_999L;
    private static final int    BOT_STOCK_QUANTITY = 99_999_999;
    private static final int    ORDER_DEPTH_LEVELS = 50;
    private static final long   PRICE_STEP         = 100L;
    private static final int    ORDERS_PER_LEVEL   = 100;

    private final MemberRepository      memberRepository;
    private final StockRepository       stockRepository;
    private final MatchTradeService     matchTradeService;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository       orderRepository;
    private final StockService          stockService;

    /**
     * 장 개장: 시스템 봇에게 유동성을 공급하고 모든 주식의 초기 호가를 생성합니다.
     *
     * @param memberId 개장을 요청한 어드민 회원 ID
     * @return 시스템 봇 ID
     */
    @Transactional
    public Long openMarket(Long memberId) {
        validateAdmin(memberId);

        Member systemBot = getOrCreateSystemBot();

        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            provisionBotStock(systemBot, stock);
            initializeStockPrices(stock);
            generateLiquidityOrders(systemBot, stock);
        }

        return systemBot.getId();
    }

    /**
     * 장 마감: 대기 중인 모든 주문을 취소하고 종가를 기록합니다.
     *
     * @param memberId 마감을 요청한 어드민 회원 ID
     */
    @Transactional
    public void closeMarket(Long memberId) {
        validateAdmin(memberId);

        // 전체 주문 로드 대신 WAITING/PARTIAL 상태만 조회
        List<StockOrder> pendingOrders = orderRepository.findPendingOrders();
        pendingOrders.forEach(order -> order.setStatus(OrderStatus.CANCELLED));

        stockService.closeMarket();
    }

    // ────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────

    private void validateAdmin(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
        if (member.getRole() != Role.ADMIN) {
            throw new BusinessException("관리자 권한이 필요합니다.");
        }
    }

    /**
     * 시스템 봇 계정을 조회하거나 최초 실행 시 생성합니다.
     */
    private Member getOrCreateSystemBot() {
        return memberRepository.findByEmail(SYSTEM_BOT_EMAIL)
                .orElseGet(() -> {
                    Member bot = new Member();
                    bot.setName(SYSTEM_BOT_NAME);
                    bot.setEmail(SYSTEM_BOT_EMAIL);
                    bot.setBalance(SYSTEM_BOT_BALANCE);
                    bot.setRole(Role.ADMIN);
                    return memberRepository.save(bot);
                });
    }

    /**
     * 시스템 봇에게 해당 주식의 매도 가능 물량을 충분히 할당합니다.
     */
    private void provisionBotStock(Member bot, Stock stock) {
        MemberStock ms = memberStockRepository
                .findByMemberIdAndStockCode(bot.getId(), stock.getStockCode())
                .orElseGet(() -> {
                    MemberStock n = new MemberStock();
                    n.setMemberId(bot.getId());
                    n.setStockCode(stock.getStockCode());
                    return n;
                });
        ms.setQuantity(BOT_STOCK_QUANTITY);
        memberStockRepository.save(ms);
    }

    /**
     * 주식의 시가, 현재가, 최고가, 최저가를 전일 종가 기준으로 초기화합니다.
     * 전일 종가가 없으면 기본값 10,000원 사용.
     */
    private void initializeStockPrices(Stock stock) {
        long rawPrice = (stock.getClosingPrice() != null && stock.getClosingPrice() > 0)
                ? stock.getClosingPrice() : 10_000L;
        long basePrice = Math.max((Math.round((double) rawPrice / 100.0)) * 100L, 100L);

        stock.setOpeningPrice(basePrice);
        stock.setCurrentPrice(basePrice);
        stock.setHighPrice(basePrice);
        stock.setLowPrice(basePrice);
        stockRepository.save(stock);
    }

    /**
     * 시스템 봇의 매수/매도 호가 100단계를 등록하여 초기 유동성을 공급합니다.
     */
    private void generateLiquidityOrders(Member bot, Stock stock) {
        long basePrice = stock.getOpeningPrice();

        for (int i = 1; i <= ORDER_DEPTH_LEVELS; i++) {
            long sellPrice = basePrice + (PRICE_STEP * i);
            matchTradeService.placeMatchOrder(
                    bot.getId(), stock.getStockCode(),
                    OrderType.LIMIT, sellPrice, ORDERS_PER_LEVEL, OrderSide.SELL);

            long buyPrice = basePrice - (PRICE_STEP * i);
            if (buyPrice > 0) {
                matchTradeService.placeMatchOrder(
                        bot.getId(), stock.getStockCode(),
                        OrderType.LIMIT, buyPrice, ORDERS_PER_LEVEL, OrderSide.BUY);
            }
        }
    }
}
