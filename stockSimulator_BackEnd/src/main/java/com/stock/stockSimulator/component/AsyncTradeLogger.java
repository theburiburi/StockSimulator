package com.stock.stockSimulator.component;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
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
    private final OrderRepository orderRepository;


    @Async("taskExecutor")
    @Transactional
    public void recordTrade(Long buyerId, Long sellerId, Long orderId,
                            String stockCode, int price, int qty) {
        RLock bStockLcok = redisson.getLock("lock:member:"+buyerId+":stock"+stockCode);
        RLock sStockLcok = redisson.getLock("lock:member:"+sellerId+":stock"+stockCode);

        RLock bMoneyLock = redisson.getLock("lock:member:"+buyerId+":money");
        RLock sMoneyLock = redisson.getLock("lock:member:"+sellerId+":money");

        RLock multiLock = redisson.getMultiLock(bStockLcok, sStockLcok, bMoneyLock, sMoneyLock);



        try {
            if(multiLock.tryLock(5,2, TimeUnit.SECONDS)){
                updateDb(buyerId, sellerId, orderId, stockCode, price, qty);
            }
        } catch (InterruptedException e) {
            log.error("체결 기록 중 락 획득 실패: {}", orderId);
        } finally {
            if(multiLock.isHeldByCurrentThread()) multiLock.unlock();
        }
        }

    @Transactional
    public void updateDb(Long buyerId, Long sellerId, Long orderId, String stockCode, int price, int quantity){
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException("매수자(ID: " + buyerId + ")를 찾을 수 없습니다."));

        MemberStock memberStock = memberStockRepository.findByMemberIdAndStockCode(sellerId, stockCode)
                .orElseThrow(() -> new BusinessException("매도자의 주식 보유 정보를 찾을 수 없습니다."));

        buyer.decreaseBalance((long) price*quantity);
        memberStock.addQuantitiy(-quantity);

        orderRepository.findById(orderId)
                .ifPresent(o -> o.applyTrade(quantity));
    }
}
