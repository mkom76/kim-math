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
 * PIPA audit record for one student-consent event.
 *
 * <p>Before consent: {@code token}, {@code tokenIssuedAt}, {@code tokenExpiresAt}
 * are set; the consent page is reached via the token. After consent the token
 * is nulled out (to prevent link replay) and the signer columns are populated.
 *
 * <p>A new row is inserted per consent event, so policy revisions (consent
 * version bump) leave the previous row intact for audit.
 */
@Entity
@Table(name = "student_consents")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "consent_version", length = 20, nullable = false)
    private String consentVersion;

    @Column(name = "token", length = 64, unique = true)
    private String token;

    @Column(name = "token_issued_at")
    private LocalDateTime tokenIssuedAt;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "consented_at")
    private LocalDateTime consentedAt;

    @Column(name = "signer_type", length = 20)
    private String signerType;

    @Column(name = "signer_phone_last4", length = 4)
    private String signerPhoneLast4;

    @Column(name = "signer_ip", length = 45)
    private String signerIp;

    @Column(name = "signer_user_agent", length = 255)
    private String signerUserAgent;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
