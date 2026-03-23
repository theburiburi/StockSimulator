package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.dto.response.NotificationDto;
import com.stock.stockSimulator.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 알림 REST API
 * - GET  /api/notifications/unread?memberId={id}   : 미읽음 알림 목록
 * - GET  /api/notifications?memberId={id}           : 전체 알림 목록
 * - POST /api/notifications/read-all?memberId={id}  : 전체 읽음 처리
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDto>> getUnread(@RequestParam Long memberId) {
        return ResponseEntity.ok(notificationService.getUnread(memberId));
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getAll(@RequestParam Long memberId) {
        return ResponseEntity.ok(notificationService.getAll(memberId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(@RequestParam Long memberId) {
        int updated = notificationService.markAllRead(memberId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
