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

@Filter(name = "academyFilter", condition = "lesson_id IN (SELECT l.id FROM lessons l WHERE l.academy_id = :academyId)")
@Filter(name = "ownerFilter",   condition = "lesson_id IN (SELECT l.id FROM lessons l WHERE l.class_id IN (SELECT ac.id FROM academy_classes ac WHERE ac.owner_teacher_id = :teacherId))")
@Entity
@Table(name = "lesson_videos")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;

    @Column(name = "youtube_video_id", nullable = false, length = 50)
    private String youtubeVideoId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "duration", length = 20)
    private String duration;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
