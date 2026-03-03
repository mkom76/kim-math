package com.example.dto;

import com.example.entity.StudentSubmission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubmissionDto {
    private Long id;
    private StudentDto student;
    private Long testId;
    private String testTitle;
    private Integer totalScore;
    private Integer pendingEssayCount;
    private Double classAverage;
    private Integer rank;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SubmissionDetailDto> details;

    public static StudentSubmissionDto from(StudentSubmission submission) {
        return StudentSubmissionDto.builder()
                .id(submission.getId())
                .student(StudentDto.from(submission.getStudent()))
                .testId(submission.getTest().getId())
                .testTitle(submission.getTest().getTitle())
                .totalScore(submission.getTotalScore())
                .submittedAt(submission.getSubmittedAt())
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}