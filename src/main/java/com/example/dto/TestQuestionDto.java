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
    private Double points;
    private QuestionType questionType;
    private String topic;
    private List<String> topicLevels;
    private TextbookProblemMetaDto textbookProblem; // null = 수동 출제

    public static TestQuestionDto from(TestQuestion question) {
        return TestQuestionDto.builder()
                .id(question.getId())
                .number(question.getNumber())
                .answer(question.getAnswer())
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
