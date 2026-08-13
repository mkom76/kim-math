package com.example.dto;

import com.example.entity.StudentNotification;

import java.time.LocalDateTime;

public record StudentNotificationDto(
        Long id,
        String type,
        String title,
        String body,
        String targetPath,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    public static StudentNotificationDto from(StudentNotification notification) {
        return new StudentNotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetPath(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
