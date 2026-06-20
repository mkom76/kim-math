package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "remember_me_tokens",
        indexes = {
                @Index(name = "idx_remember_me_user", columnList = "user_role,user_id"),
                @Index(name = "idx_remember_me_expires", columnList = "expires_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_remember_me_selector",
                columnNames = "selector_hash"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RememberMeToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "selector_hash", nullable = false, length = 64)
    private String selectorHash;

    @Column(name = "validator_hash", nullable = false, length = 64)
    private String validatorHash;

    @Column(name = "user_role", nullable = false, length = 20)
    private String userRole;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
