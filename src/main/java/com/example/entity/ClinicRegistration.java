package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@FilterDef(name = "academyFilter", parameters = @ParamDef(name = "academyId", type = Long.class))
@FilterDef(name = "ownerFilter",   parameters = @ParamDef(name = "teacherId", type = Long.class))
@Filter(name = "academyFilter", condition = "student_id IN (SELECT id FROM students WHERE academy_id = :academyId)")
@Filter(name = "ownerFilter",   condition = "student_id IN (SELECT id FROM students WHERE class_id IN (SELECT id FROM academy_classes WHERE owner_teacher_id = :teacherId))")
@Entity
@Table(name = "clinic_registrations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"clinic_id", "student_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ClinicRegistrationStatus status = ClinicRegistrationStatus.REGISTERED;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
