package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.MemberStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberStockRepository extends JpaRepository<MemberStock, Long> {

    Optional<MemberStock> findByMemberIdAndStockCode(Long memberId, String stockCode);
    List<MemberStock> findAllByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ms FROM MemberStock ms WHERE ms.memberId = :memberId AND ms.stockCode = :stockCode")
    Optional<MemberStock> findByMemberIdAndStockCodeWithLock(@Param("memberId") Long memberId, @Param("stockCode") String stockCode);
}