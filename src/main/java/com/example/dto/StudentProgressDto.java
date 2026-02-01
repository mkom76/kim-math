package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProgressDto {
    private Long studentId;
    private String studentName;
    private Integer progressPercent;
    private Boolean completed;
    private LocalDateTime lastWatchedAt;
}
