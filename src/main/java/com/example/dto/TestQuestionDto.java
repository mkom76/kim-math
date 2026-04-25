package com.example.dto;

import com.example.entity.QuestionType;
import com.example.entity.TestQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestQuestionDto {
    private Long id;
    private Integer number;
    private String answer;
    private Double points;
    private QuestionType questionType;
    private TextbookProblemMetaDto textbookProblem; // null = 수동 출제

    public static TestQuestionDto from(TestQuestion question) {
        return TestQuestionDto.builder()
                .id(question.getId())
                .number(question.getNumber())
                .answer(question.getAnswer())
                .points(question.getPoints())
                .questionType(question.getQuestionType())
                .textbookProblem(TextbookProblemMetaDto.fromOrNull(question.getTextbookProblem()))
                .build();
    }
}