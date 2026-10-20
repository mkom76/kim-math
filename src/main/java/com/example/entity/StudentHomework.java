package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SqlFragmentAlias;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Filter(name = "academyFilter", condition = "student_id IN (SELECT s2.id FROM students s2 WHERE s2.academy_id = :academyId)")
@Filter(name = "ownerFilter", deduceAliasInjectionPoints = false,
        aliases = @SqlFragmentAlias(alias = "progress", table = "student_homeworks"),
        condition = """
                EXISTS (SELECT 1 FROM student_homework_classes shc JOIN academy_classes ac ON ac.id = shc.class_id
                    WHERE shc.student_homework_id = {progress}.id
                    AND (ac.owner_teacher_id = :teacherId OR ac.id IN
                        (SELECT ca.class_id FROM class_assistants ca WHERE ca.teacher_id = :teacherId)))
                """)
@Entity
@Table(name = "student_homeworks", uniqueConstraints = @UniqueConstraint(
        name = "uk_student_homeworks_student_homework_lesson", columnNames = {"student_id", "homework_id", "lesson_scope_id"}))
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Lesson lesson;

    // NULL represents legacy homework entered without a lesson; it still has one record per student.
    @org.hibernate.annotations.GeneratedColumn("coalesce(lesson_id, 0)")
    @Column(name = "lesson_scope_id", insertable = false, updatable = false)
    private Long lessonScopeId;

    @ManyToMany
    @JoinTable(name = "student_homework_classes",
            joinColumns = @JoinColumn(name = "student_homework_id"),
            inverseJoinColumns = @JoinColumn(name = "class_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"student_homework_id", "class_id"}))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<AcademyClass> classHistory = new ArrayList<>();

    @PrePersist
    private void recordInitialClass() {
        if (lesson == null) lesson = homework.getLesson();
        if (classHistory.isEmpty()) classHistory.add(lesson != null ? lesson.getAcademyClass() : homework.getAcademyClass());
    }

    public void recordClass(AcademyClass academyClass) {
        if (classHistory.stream().noneMatch(item -> item.getId().equals(academyClass.getId()))) {
            classHistory.add(academyClass);
        }
    }

    @Column(name = "incorrect_count")
    private Integer incorrectCount; // 오답 개수 (null = 미제출)

    @Column(name = "unsolved_count")
    private Integer unsolvedCount; // 미제출(풀지 않은) 문제 개수

    @Column(name = "incorrect_questions", length = 500)
    private String incorrectQuestions; // 오답 문항번호 (예: "1,3,5")

    @Column(name = "unsolved_questions", length = 500)
    private String unsolvedQuestions; // 안 푼 문항번호 (예: "2,7")

    @Column(name = "questioned_questions", length = 500)
    private String questionedQuestions; // 학생이 질문하고 싶은 문항번호 (예: "3,7,12")

    @Column(name = "follow_up_flag", nullable = false)
    @Builder.Default
    private Boolean followUpFlag = false;

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
