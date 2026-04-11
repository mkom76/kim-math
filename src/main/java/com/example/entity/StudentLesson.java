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
@Table(name = "student_lessons",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "lesson_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLesson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "instructor_feedback", columnDefinition = "TEXT")
    private String instructorFeedback;

    @Column(name = "feedback_author")
    private String feedbackAuthor;

    @Column(name = "is_ai_feedback")
    @Builder.Default
    private Boolean isAiFeedback = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status")
    private AttendanceStatus attendanceStatus;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
