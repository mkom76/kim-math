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

@Entity
@Table(name = "student_video_progress")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVideoProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_video_id", nullable = false)
    private LessonVideo lessonVideo;

    @Column(name = "watched_time", nullable = false)
    private Integer watchedTime = 0;

    @Column(name = "duration", nullable = false)
    private Integer duration = 0;

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Integer getProgressPercent() {
        if (duration == null || duration == 0) {
            return 0;
        }
        return (watchedTime * 100) / duration;
    }
}
