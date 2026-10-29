package com.example.dto;

import com.example.entity.StudentSubmission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private Long classId;
    private String className;
    private LocalDateTime classEndedAt;
    private LocalDate lessonDate;
    private LocalDateTime testCreatedAt;
    private ErrorCausesDto errorCauses;
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
                .classId(submission.getTest().getAcademyClass() != null
                        ? submission.getTest().getAcademyClass().getId() : null)
                .className(submission.getTest().getAcademyClass() != null
                        ? submission.getTest().getAcademyClass().getName() : null)
                .classEndedAt(submission.getTest().getAcademyClass() != null
                        ? submission.getTest().getAcademyClass().getEndedAt() : null)
                .lessonDate(submission.getTest().getLesson() != null
                        ? submission.getTest().getLesson().getLessonDate() : null)
                .testCreatedAt(submission.getTest().getCreatedAt())
                .errorCauses(ErrorCausesDto.from(submission))
                .totalScore(submission.getTotalScore())
                .submittedAt(submission.getSubmittedAt())
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}
