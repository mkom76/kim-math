package com.example.service;

import com.example.dto.StudentNotificationDto;
import com.example.entity.StudentNotification;
import com.example.exception.ForbiddenException;
import com.example.repository.StudentNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentNotificationService {
    private static final int INBOX_LIMIT = 100;

    private final StudentNotificationRepository repository;

    @Transactional
    public void createForStudents(List<Long> studentIds,
                                  String type,
                                  String title,
                                  String body,
                                  String targetPath,
                                  String sourceKey) {
        for (Long studentId : studentIds.stream().distinct().toList()) {
            if (sourceKey != null && repository.existsByStudentIdAndSourceKey(studentId, sourceKey)) {
                continue;
            }
            repository.save(StudentNotification.builder()
                    .studentId(studentId)
                    .type(type == null || type.isBlank() ? "GENERAL" : type)
                    .title(title)
                    .body(body)
                    .targetPath(targetPath)
                    .sourceKey(sourceKey)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<StudentNotificationDto> getInbox(Long studentId) {
        return repository.findByStudentIdOrderByCreatedAtDesc(
                        studentId, PageRequest.of(0, INBOX_LIMIT)).stream()
                .map(StudentNotificationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long studentId) {
        return repository.countByStudentIdAndReadAtIsNull(studentId);
    }

    @Transactional
    public StudentNotificationDto markRead(Long studentId, Long notificationId) {
        StudentNotification notification = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
        if (!notification.getStudentId().equals(studentId)) {
            throw new ForbiddenException("다른 학생의 알림에 접근할 수 없습니다");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        return StudentNotificationDto.from(notification);
    }

    @Transactional
    public int markAllRead(Long studentId) {
        return repository.markAllRead(studentId, LocalDateTime.now());
    }
}
