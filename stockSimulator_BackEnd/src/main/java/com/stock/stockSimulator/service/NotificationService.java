package com.stock.stockSimulator.service;

import com.stock.stockSimulator.domain.Notification;
import com.stock.stockSimulator.domain.NotificationType;
import com.stock.stockSimulator.dto.response.NotificationDto;
import com.stock.stockSimulator.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessageSendingOperations messageTemplate;

    /**
     * 알림을 DB에 저장하고 해당 사용자의 WebSocket 채널로 push합니다.
     *
     * @param memberId 수신할 회원 ID
     * @param message  알림 메시지
     * @param type     알림 유형
     */
    @Transactional
    public void send(Long memberId, String message, NotificationType type) {
        Notification notification = new Notification(memberId, message, type);
        notificationRepository.save(notification);

        NotificationDto dto = new NotificationDto(notification);

        // /user/{memberId}/queue/notifications 채널로 전송
        // SimpMessageSendingOperations.convertAndSendToUser()는 내부적으로
        // /user/{username}/queue/notifications 경로로 라우팅됩니다.
        messageTemplate.convertAndSendToUser(
                String.valueOf(memberId),
                "/queue/notifications",
                dto
        );
        log.info("알림 전송 완료 - memberId: {}, message: {}", memberId, message);
    }

    /**
     * 미읽음 알림 목록을 반환합니다.
     */
    public List<NotificationDto> getUnread(Long memberId) {
        return notificationRepository
                .findByMemberIdAndIsReadFalseOrderByCreatedAtDesc(memberId)
                .stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 전체 알림 목록을 반환합니다.
     */
    public List<NotificationDto> getAll(Long memberId) {
        return notificationRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 해당 회원의 미읽음 알림을 모두 읽음 처리합니다.
     */
    @Transactional
    public int markAllRead(Long memberId) {
        return notificationRepository.markAllReadByMemberId(memberId);
    }
}
