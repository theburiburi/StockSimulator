package com.stock.stockSimulator.service;

import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final MemberRepository memberRepository;
    private final StockRepository stockRepository;
    private final MatchTradeService matchTradeService;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Transactional
    public Long openMarket() {
        // 1. Get or Create Liquidity Provider Dummy User (User 2 logic)
        Member admin = memberRepository.findByEmail("admin@admin.com")
            .orElseGet(() -> {
                Member m = new Member();
                m.setName("Liquidity Provider (User 2)");
                m.setEmail("admin@admin.com");
                m.setBalance(999_999_999_999_999L); // Prevent overflow, huge balance
                m.setRole(Role.ADMIN);
                return memberRepository.save(m);
            });

        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            // Give bot ample stocks to sell
            MemberStock ms = memberStockRepository.findByMemberIdAndStockCode(admin.getId(), stock.getStockCode())
                .orElseGet(() -> {
                    MemberStock n = new MemberStock();
                    n.setMemberId(admin.getId());
                    n.setStockCode(stock.getStockCode());
                    return n;
                });
            ms.setQuantity(99999999);
            memberStockRepository.save(ms);

            long rawPrice = stock.getClosingPrice() != null && stock.getClosingPrice() > 0 ? stock.getClosingPrice() : 10000L;
            long basePrice = (Math.round((double) rawPrice / 100.0)) * 100L;
            if (basePrice <= 0) basePrice = 100L;
            
            // Initialize the metrics for the start of the market session
            stock.setOpeningPrice(basePrice);
            stock.setCurrentPrice(basePrice);
            stock.setHighPrice(basePrice);
            stock.setLowPrice(basePrice);
            stockRepository.save(stock);
            
            long step = 100; // Force 100 KRW intervals

            // Generate 50 SELL orders (Higher prices)
            for (int i = 1; i <= 50; i++) {
                long price = basePrice + (step * i);
                matchTradeService.placeMatchOrder(admin.getId(), stock.getStockCode(), OrderType.LIMIT, price, 100, OrderSide.SELL);
            }
            
            // Generate 50 BUY orders (Lower prices)
            for (int i = 1; i <= 50; i++) {
                long price = basePrice - (step * i);
                if (price > 0) {
                    matchTradeService.placeMatchOrder(admin.getId(), stock.getStockCode(), OrderType.LIMIT, price, 100, OrderSide.BUY);
                }
            }
        }
        
        return admin.getId();
    }

    @Transactional
    public void closeMarket() {
        // Cancel all pending orders
        List<StockOrder> pendingOrders = orderRepository.findAll();
        for (StockOrder order : pendingOrders) {
            if (order.getStatus() == OrderStatus.WAITING || order.getStatus() == OrderStatus.PARTIAL) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        }
        
        // Update ending prices (requires StockService.closeMarket())
        stockService.closeMarket();
    }
}
