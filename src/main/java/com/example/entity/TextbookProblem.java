package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "textbook_problems")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextbookProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "textbook_id", nullable = false)
    private Textbook textbook;

    @Column(nullable = false)
    private Integer number;

    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType; // nullable — 시험 출제 시 스냅샷에서 null이면 디폴트(SUBJECTIVE) 사용

    @Column
    private String topic;

    @Column(name = "video_link", length = 1024)
    private String videoLink;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
