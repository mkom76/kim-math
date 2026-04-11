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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "homeworks")
@EntityListeners(AuditingEntityListener.class)
@FilterDef(name = "academyFilter", parameters = @ParamDef(name = "academyId", type = Long.class))
@FilterDef(name = "ownerFilter",   parameters = @ParamDef(name = "teacherId", type = Long.class))
@Filter(name = "academyFilter", condition = "academy_id = :academyId")
@Filter(name = "ownerFilter",   condition = "class_id IN (SELECT id FROM academy_classes WHERE owner_teacher_id = :teacherId)")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Homework {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Integer questionCount;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private AcademyClass academyClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @OneToMany(mappedBy = "homework", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudentHomework> studentHomeworks = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
