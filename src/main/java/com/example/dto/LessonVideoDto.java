package com.example.dto;

import com.example.entity.LessonVideo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonVideoDto {
    private Long id;
    private Long lessonId;
    private String youtubeUrl;
    private String youtubeVideoId;
    private String title;
    private String thumbnailUrl;
    private String duration;
    private Integer orderIndex;
    private LocalDateTime createdAt;

    public static LessonVideoDto from(LessonVideo video) {
        return LessonVideoDto.builder()
                .id(video.getId())
                .lessonId(video.getLesson().getId())
                .youtubeUrl(video.getYoutubeUrl())
                .youtubeVideoId(video.getYoutubeVideoId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .duration(video.getDuration())
                .orderIndex(video.getOrderIndex())
                .createdAt(video.getCreatedAt())
                .build();
    }
}
