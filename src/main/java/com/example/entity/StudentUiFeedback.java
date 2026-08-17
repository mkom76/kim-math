package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_ui_feedback")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentUiFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "sentiment", length = 24, nullable = false)
    private String sentiment;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "page_path", length = 255, nullable = false)
    private String pagePath;

    @Column(name = "ui_version", length = 20, nullable = false)
    private String uiVersion;

    @Column(name = "viewport_width")
    private Integer viewportWidth;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
