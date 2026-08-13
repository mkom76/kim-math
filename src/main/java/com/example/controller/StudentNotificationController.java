package com.example.controller;

import com.example.dto.StudentNotificationDto;
import com.example.exception.ForbiddenException;
import com.example.service.StudentNotificationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student-notifications")
@RequiredArgsConstructor
public class StudentNotificationController {
    private final StudentNotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<StudentNotificationDto>> getInbox(HttpSession session) {
        return ResponseEntity.ok(notificationService.getInbox(requireStudentId(session)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpSession session) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(requireStudentId(session))));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<StudentNotificationDto> markRead(
            @PathVariable Long notificationId, HttpSession session) {
        return ResponseEntity.ok(notificationService.markRead(requireStudentId(session), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(HttpSession session) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(requireStudentId(session))));
    }

    private Long requireStudentId(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");
        if (userId == null || !"STUDENT".equals(userRole)) {
            throw new ForbiddenException("학생 로그인이 필요합니다");
        }
        return userId;
    }
}
