package com.example.dto;

import com.example.entity.QuestionType;
import com.example.entity.TestQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestQuestionDto {
    private Long id;
    private Integer number;
    private String answer;
    private Boolean multipleAnswers;
    private Double points;
    private QuestionType questionType;
    private String topic;
    private List<String> topicLevels;
    private TextbookProblemMetaDto textbookProblem; // null = 수동 출제

    public static TestQuestionDto from(TestQuestion question) {
        return from(question, true);
    }

    public static TestQuestionDto from(TestQuestion question, boolean includeAnswer) {
        return TestQuestionDto.builder()
                .id(question.getId())
                .number(question.getNumber())
                .answer(includeAnswer ? question.getAnswer() : null)
                .multipleAnswers(Boolean.TRUE.equals(question.getMultipleAnswers()))
                .points(question.getPoints())
                .questionType(question.getQuestionType())
                .topic(question.getTopic())
                .topicLevels(collectLevels(question))
                .textbookProblem(TextbookProblemMetaDto.fromOrNull(question.getTextbookProblem()))
                .build();
    }

    private static List<String> collectLevels(TestQuestion question) {
        List<String> levels = new ArrayList<>(5);
        String[] stored = {
                question.getTopicL1(), question.getTopicL2(), question.getTopicL3(),
                question.getTopicL4(), question.getTopicL5()
        };
        for (String level : stored) {
            if (level == null || level.isBlank()) break;
            levels.add(level);
        }
        return levels;
    }
}
