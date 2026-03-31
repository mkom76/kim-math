package com.example.dto;

import com.example.entity.StudentHomework;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentHomeworkDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long homeworkId;
    private String homeworkTitle;
    private Integer questionCount; // 전체 문제 수
    private LocalDate dueDate; // 제출 기한
    private Integer incorrectCount; // 오답 개수
    private Integer unsolvedCount; // 미제출(풀지 않은) 문제 개수
    private String incorrectQuestions; // 오답 문항번호
    private String unsolvedQuestions; // 안 푼 문항번호
    private Integer completion; // 완성도 (계산된 값, 0-100)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentHomeworkDto from(StudentHomework entity) {
        return StudentHomeworkDto.builder()
                .id(entity.getId())
                .studentId(entity.getStudent().getId())
                .studentName(entity.getStudent().getName())
                .homeworkId(entity.getHomework().getId())
                .homeworkTitle(entity.getHomework().getTitle())
                .questionCount(entity.getHomework().getQuestionCount())
                .dueDate(entity.getHomework().getDueDate())
                .incorrectCount(entity.getIncorrectCount())
                .unsolvedCount(entity.getUnsolvedCount())
                .incorrectQuestions(entity.getIncorrectQuestions())
                .unsolvedQuestions(entity.getUnsolvedQuestions())
                .completion(entity.getCompletion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
