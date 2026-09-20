package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Immutable audit trail for a deleted legacy student id. */
@Entity
@Table(name = "student_account_merges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_account_merges_source",
                columnNames = "source_student_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountMerge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "source_student_id", nullable = false)
    private Long sourceStudentId;

    @Column(name = "target_student_id", nullable = false)
    private Long targetStudentId;

    @Column(name = "merged_by_teacher_id", nullable = false)
    private Long mergedByTeacherId;

    @Column(name = "merged_at", nullable = false)
    private LocalDateTime mergedAt;
}
