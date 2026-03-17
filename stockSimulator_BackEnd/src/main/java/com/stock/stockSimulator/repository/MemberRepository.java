package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("select m from Member m where m.id = :id")
//    Optional<Member> findByIdWithLock(Long id);
}


