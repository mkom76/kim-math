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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@FilterDef(name = "academyFilter", parameters = @ParamDef(name = "academyId", type = Long.class))
@FilterDef(name = "ownerFilter",   parameters = @ParamDef(name = "teacherId", type = Long.class))
@Filter(name = "academyFilter", condition = "class_id IN (SELECT id FROM academy_classes WHERE academy_id = :academyId)")
@Filter(name = "ownerFilter",   condition = "class_id IN (SELECT id FROM academy_classes WHERE owner_teacher_id = :teacherId)")
@Entity
@Table(name = "clinics")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clinic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private AcademyClass academyClass;

    @Column(name = "clinic_date", nullable = false)
    private LocalDate clinicDate;

    @Column(name = "clinic_time", nullable = false)
    private LocalTime clinicTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ClinicStatus status = ClinicStatus.OPEN;

    @OneToMany(mappedBy = "clinic", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClinicRegistration> registrations = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
