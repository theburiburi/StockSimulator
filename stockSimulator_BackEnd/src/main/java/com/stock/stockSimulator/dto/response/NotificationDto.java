package com.stock.stockSimulator.dto.response;

import com.stock.stockSimulator.domain.Notification;
import com.stock.stockSimulator.domain.NotificationType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationDto {
    private final Long id;
    private final Long memberId;
    private final String message;
    private final NotificationType type;
    private final boolean isRead;
    private final LocalDateTime createdAt;

    public NotificationDto(Notification notification) {
        this.id = notification.getId();
        this.memberId = notification.getMemberId();
        this.message = notification.getMessage();
        this.type = notification.getType();
        this.isRead = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }
}
