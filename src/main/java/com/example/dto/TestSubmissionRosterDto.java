package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSubmissionRosterDto {
    private Long studentId;
    private String studentName;
    private String grade;
    private String school;
    private Boolean submitted;
    private Long submissionId;
    private Integer score;
    private Integer pendingEssayCount;
    private LocalDateTime submittedAt;
}
