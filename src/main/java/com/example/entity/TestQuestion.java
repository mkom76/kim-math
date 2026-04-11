package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Filter(name = "academyFilter", condition = "test_id IN (SELECT t.id FROM tests t WHERE t.academy_id = :academyId)")
@Filter(name = "ownerFilter",   condition = "test_id IN (SELECT t.id FROM tests t WHERE t.class_id IN (SELECT ac.id FROM academy_classes ac WHERE ac.owner_teacher_id = :teacherId))")
@Entity
@Table(name = "test_questions")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @Column(nullable = false)
    private Integer number;

    private String answer;

    @Column(nullable = false)
    private Double points;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    @Builder.Default
    private QuestionType questionType = QuestionType.SUBJECTIVE;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    @Builder.Default
    private List<StudentSubmissionDetail> submissionDetails = new ArrayList<>();
}