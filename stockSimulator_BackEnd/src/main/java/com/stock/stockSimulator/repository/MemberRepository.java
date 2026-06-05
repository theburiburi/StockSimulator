package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.balance = m.balance + :amount where m.id = :memberId")
    int addBalance(@Param("memberId") Long memberId, @Param("amount") long amount);
}
