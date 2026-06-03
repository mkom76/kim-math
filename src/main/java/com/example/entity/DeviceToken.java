package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One per (student, device) pair. A token uniquely identifies a device install
 * — re-registering the same token from a different student moves ownership
 * (the prior student loses it). FCM rotates tokens on app reinstall so the
 * old row will simply go silent and can be reaped by a TTL job later.
 */
@Entity
@Table(name = "device_tokens",
       uniqueConstraints = @UniqueConstraint(name = "uq_device_tokens_token", columnNames = "token"))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "token", length = 255, nullable = false)
    private String token;

    @Column(name = "platform", length = 16, nullable = false)
    private String platform; // "android" | "ios"

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
