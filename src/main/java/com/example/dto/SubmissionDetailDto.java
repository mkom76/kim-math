package com.example.dto;

import com.example.entity.StudentSubmissionDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDetailDto {
    private Long id;
    private Integer questionNumber;
    private String studentAnswer;
    private String correctAnswer;
    private Boolean isCorrect;
    private Double earnedPoints;
    private Double maxPoints;
    private String teacherComment;
    private String questionType;

    public static SubmissionDetailDto from(StudentSubmissionDetail detail) {
        return SubmissionDetailDto.builder()
                .id(detail.getId())
                .questionNumber(detail.getQuestion().getNumber())
                .studentAnswer(detail.getStudentAnswer())
                .correctAnswer(detail.getQuestion().getAnswer())
                .isCorrect(detail.getIsCorrect())
                .earnedPoints(detail.getEarnedPoints())
                .maxPoints(detail.getQuestion().getPoints())
                .teacherComment(detail.getTeacherComment())
                .questionType(detail.getQuestion().getQuestionType().name())
                .build();
    }
}