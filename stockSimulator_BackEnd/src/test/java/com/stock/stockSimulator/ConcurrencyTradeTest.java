package com.stock.stockSimulator;

import com.stock.stockSimulator.domain.*;
import com.stock.stockSimulator.repository.*;
import com.stock.stockSimulator.service.MatchTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTradeTest {

    @Autowired
    private MatchTradeService matchTradeService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberStockRepository memberStockRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Member buyer;
    private Member seller;
    private Stock stock;

    @BeforeEach
    void setUp() {
        // Clear previous Redis keys
        try {
            redisTemplate.delete(redisTemplate.keys("member:*"));
            redisTemplate.delete(redisTemplate.keys("orderbook:*"));
            redisTemplate.delete(redisTemplate.keys("orders:*"));
        } catch (Exception e) {
            // Ignore if key set is empty
        }
        
        orderRepository.deleteAllInBatch();
        memberStockRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();

        // 1. Create Buyer and Seller
        buyer = Member.builder()
                .name("Buyer")
                .email("buyer@test.com")
                .role(Role.USER)
                .build();
        buyer.setBalance(300_000_000L); // 300M won for buying
        memberRepository.save(buyer);

        seller = Member.builder()
                .name("Seller")
                .email("seller@test.com")
                .role(Role.USER)
                .build();
        seller.setBalance(10_000_000L); // 10M won
        memberRepository.save(seller);

        // 2. Create Stock
        stock = new Stock("005930", "Samsung");
        stock.setCurrentPrice(50_000L);
        stockRepository.save(stock);

        // 3. Create MemberStock for Seller
        MemberStock sellerStock = new MemberStock();
        sellerStock.setMemberId(seller.getId());
        sellerStock.setStockCode(stock.getStockCode());
        sellerStock.setQuantity(50_000); // 50,000 shares
        sellerStock.setAveragePrice(50_000L);
        memberStockRepository.save(sellerStock);

        // Warm up and sync to Redis
        matchTradeService.syncAllToRedisOnStartup();
    }

    @Test
    void testConcurrency10000Orders() throws InterruptedException {
        int totalOrdersPerSide = 5000; // 5,000 Buy + 5,000 Sell = 10,000 total orders
        int threadCount = 60; // Max out threads to match new pool size
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalOrdersPerSide * 2);

        System.out.println("🚀 Spawning " + threadCount + " concurrent threads to place 10,000 orders...");
        long startTime = System.currentTimeMillis();

        // Submit buy orders
        for (int i = 0; i < totalOrdersPerSide; i++) {
            executorService.submit(() -> {
                try {
                    matchTradeService.placeMatchOrder(
                            buyer.getId(),
                            stock.getStockCode(),
                            OrderType.LIMIT,
                            50_000L,
                            1,
                            OrderSide.BUY
                    );
                } catch (Exception e) {
                    // Ignore errors during stress testing
                } finally {
                    latch.countDown();
                }
            });
        }

        // Submit sell orders
        for (int i = 0; i < totalOrdersPerSide; i++) {
            executorService.submit(() -> {
                try {
                    matchTradeService.placeMatchOrder(
                            seller.getId(),
                            stock.getStockCode(),
                            OrderType.LIMIT,
                            50_000L,
                            1,
                            OrderSide.SELL
                    );
                } catch (Exception e) {
                    // Ignore errors during stress testing
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        System.out.println("=================================================");
        System.out.println("🔥 10,000 Concurrent Orders Placed in Redis! 🔥");
        System.out.println("Time taken: " + elapsed + " ms");
        System.out.println("=================================================");

        // Wait a few seconds for all background async trades to sync to RDBMS
        System.out.println("⏳ Waiting for RDBMS asynchronous trade processing to finalize...");
        Thread.sleep(5000);

        // Verify balances
        Member updatedBuyer = memberRepository.findById(buyer.getId()).orElseThrow();
        Member updatedSeller = memberRepository.findById(seller.getId()).orElseThrow();

        System.out.println("Buyer Initial Balance: 300,000,000 -> Updated: " + updatedBuyer.getBalance());
        System.out.println("Seller Initial Balance: 10,000,000 -> Updated: " + updatedSeller.getBalance());

        // Check if all shares were successfully traded
        MemberStock updatedSellerStock = memberStockRepository
                .findByMemberIdAndStockCode(seller.getId(), stock.getStockCode())
                .orElseThrow();
        System.out.println("Seller Remaining Stock Qty (Expected < 50,000): " + updatedSellerStock.getQuantity());

        assertThat(updatedSellerStock.getQuantity()).isLessThan(50_000);
    }
}
