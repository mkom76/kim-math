package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 숙제와 교재 문제(또는 수동 슬롯)를 잇는 조인 엔티티.
 * - textbookProblem이 null이면 수동 슬롯 (메타데이터 없음)
 * - position은 숙제 안에서의 1..N번 (학생에게 보이는 번호)
 */
@Entity
@Table(name = "homework_problems",
        uniqueConstraints = @UniqueConstraint(columnNames = {"homework_id", "position"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeworkProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_id", nullable = false)
    private Homework homework;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "textbook_problem_id")
    private TextbookProblem textbookProblem; // null = 수동 슬롯

    @Column(nullable = false)
    private Integer position;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
