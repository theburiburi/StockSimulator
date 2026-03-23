package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdAndIsReadFalseOrderByCreatedAtDesc(Long memberId);

    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.memberId = :memberId AND n.isRead = false")
    int markAllReadByMemberId(@Param("memberId") Long memberId);
}
