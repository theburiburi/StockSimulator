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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTradeLogger {
    private final RedissonClient redisson;
    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;


    @Async("taskExecutor")
    @Transactional
    public void recordTrade(Long buyerId, Long sellerId, Long orderId,
                            String stockCode, int price, int qty) {
        RLock lock = redisson.getLock("lock:trade:"+buyerId+":"+sellerId);
        try {
    if(lock.tryLock(5,2, TimeUnit.SECONDS)){
        updateDb(buyerId, sellerId, orderId, stockCode, price, qty);
    }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Transactional
    public void updateDb(Long buyerId, Long sellerId, Long orderId, String stockCode, int price, int quantity){
        Member buyer = memberRepository.findById(buyerId).orElseThrow();
        buyer.decreaseBalance((long) price*quantity);
        MemberStock memberStock = memberStockRepository.findByMemberIdAndStockCode(sellerId, stockCode).orElseThrow();
        memberStock.addQuantitiy(-quantity);
        orderRepository.findById(orderId).ifPresent(o -> o.applyTrade(quantity));
    }
}
