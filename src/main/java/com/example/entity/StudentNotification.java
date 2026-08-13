package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** A durable, student-owned copy of a notification sent through FCM. */
@Entity
@Table(name = "student_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_student_notifications_source",
                columnNames = {"student_id", "source_key"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "title", length = 120, nullable = false)
    private String title;

    @Column(name = "body", length = 500, nullable = false)
    private String body;

    @Column(name = "target_path", length = 255)
    private String targetPath;

    @Column(name = "source_key", length = 120)
    private String sourceKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
