package com.example.dto;

import com.example.entity.StudentVideoProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentVideoProgressDto {
    private Long videoId;
    private Integer watchedTime;
    private Integer duration;
    private Integer progressPercent;
    private Boolean completed;
    private LocalDateTime lastWatchedAt;

    public static StudentVideoProgressDto from(StudentVideoProgress progress) {
        return StudentVideoProgressDto.builder()
                .videoId(progress.getLessonVideo().getId())
                .watchedTime(progress.getWatchedTime())
                .duration(progress.getDuration())
                .progressPercent(progress.getProgressPercent())
                .completed(progress.getCompleted())
                .lastWatchedAt(progress.getLastWatchedAt())
                .build();
    }
}
