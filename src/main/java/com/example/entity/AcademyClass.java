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

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "academy_classes")
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "academyFilter", condition = "academy_id = :academyId")
@Filter(name = "ownerFilter",   condition = "owner_teacher_id = :teacherId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademyClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @Column(name = "owner_teacher_id")
    private Long ownerTeacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinic_day_of_week")
    private DayOfWeek clinicDayOfWeek;

    @Column(name = "clinic_time")
    private LocalTime clinicTime;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "academyClass", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Student> students = new ArrayList<>();

    @OneToMany(mappedBy = "academyClass", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Test> tests = new ArrayList<>();

    @OneToMany(mappedBy = "academyClass", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Homework> homeworks = new ArrayList<>();
}
