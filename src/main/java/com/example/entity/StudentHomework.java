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
@Table(name = "student_homeworks")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentHomework {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_id", nullable = false)
    private Homework homework;

    @Column(name = "incorrect_count")
    private Integer incorrectCount; // 오답 개수 (null = 미제출)

    @Column(name = "unsolved_count")
    private Integer unsolvedCount; // 미제출(풀지 않은) 문제 개수

    @Column(name = "incorrect_questions", length = 500)
    private String incorrectQuestions; // 오답 문항번호 (예: "1,3,5")

    @Column(name = "unsolved_questions", length = 500)
    private String unsolvedQuestions; // 안 푼 문항번호 (예: "2,7")

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 완성도 계산 (0-100), null이면 미제출 상태
    public Integer getCompletion() {
        // 미제출 상태 (incorrectCount가 null이면 아직 채점 안됨)
        if (incorrectCount == null) {
            return null;
        }

        if (homework == null || homework.getQuestionCount() == null || homework.getQuestionCount() == 0) {
            return 0;
        }

        int total = homework.getQuestionCount();
        int incorrect = incorrectCount;
        int unsolved = unsolvedCount != null ? unsolvedCount : 0;

        // 완성도 = (전체 - 오답 - 미제출) / 전체 * 100
        int correct = total - incorrect - unsolved;
        if (correct < 0) correct = 0; // 음수 방지

        return (int) Math.round(((double) correct / total) * 100);
    }
}
