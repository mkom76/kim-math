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

@Filter(name = "academyFilter", condition = "submission_id IN (SELECT ss.id FROM student_submissions ss WHERE ss.student_id IN (SELECT s2.id FROM students s2 WHERE s2.academy_id = :academyId))")
@Filter(name = "ownerFilter",   condition = "submission_id IN (SELECT ss.id FROM student_submissions ss WHERE ss.student_id IN (SELECT s2.id FROM students s2 WHERE s2.class_id IN (SELECT ac.id FROM academy_classes ac WHERE ac.owner_teacher_id = :teacherId)))")
@Entity
@Table(name = "student_submission_details")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubmissionDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private StudentSubmission submission;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private TestQuestion question;

    @Column(name = "student_answer")
    private String studentAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "earned_points")
    private Double earnedPoints;

    @Column(name = "teacher_comment", length = 500)
    private String teacherComment;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}