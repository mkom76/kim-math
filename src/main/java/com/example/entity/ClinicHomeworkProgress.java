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

@Filter(name = "academyFilter", condition = "student_id IN (SELECT s2.id FROM students s2 WHERE s2.academy_id = :academyId)")
@Filter(name = "ownerFilter",   condition = "student_id IN (SELECT s2.id FROM students s2 WHERE s2.class_id IN (SELECT ac.id FROM academy_classes ac WHERE ac.owner_teacher_id = :teacherId))")
@Entity
@Table(name = "clinic_homework_progress")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicHomeworkProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_id", nullable = false)
    private Homework homework;

    // 클리닉 전 상태
    @Column(name = "incorrect_count_before")
    private Integer incorrectCountBefore;

    @Column(name = "unsolved_count_before")
    private Integer unsolvedCountBefore;

    // 클리닉 후 상태
    @Column(name = "incorrect_count_after")
    private Integer incorrectCountAfter;

    @Column(name = "unsolved_count_after")
    private Integer unsolvedCountAfter;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 완성도 계산 헬퍼 메서드
    public Integer getCompletionBefore() {
        if (incorrectCountBefore == null || homework == null || homework.getQuestionCount() == null) {
            return null;
        }
        int total = homework.getQuestionCount();
        int incorrect = incorrectCountBefore;
        int unsolved = unsolvedCountBefore != null ? unsolvedCountBefore : 0;
        int correct = total - incorrect - unsolved;
        if (correct < 0) correct = 0;
        return (int) Math.round(((double) correct / total) * 100);
    }

    public Integer getCompletionAfter() {
        if (incorrectCountAfter == null || homework == null || homework.getQuestionCount() == null) {
            return null;
        }
        int total = homework.getQuestionCount();
        int incorrect = incorrectCountAfter;
        int unsolved = unsolvedCountAfter != null ? unsolvedCountAfter : 0;
        int correct = total - incorrect - unsolved;
        if (correct < 0) correct = 0;
        return (int) Math.round(((double) correct / total) * 100);
    }

    // 완성도 변화량 (% 포인트)
    public Integer getCompletionChange() {
        Integer before = getCompletionBefore();
        Integer after = getCompletionAfter();
        if (before == null || after == null) {
            return null;
        }
        return after - before;
    }
}
