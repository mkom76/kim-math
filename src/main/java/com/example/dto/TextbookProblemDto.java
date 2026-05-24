package com.example.dto;

import com.example.entity.QuestionType;
import com.example.entity.TextbookProblem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextbookProblemDto {
    private Long id;
    private Long textbookId;
    private Integer number;
    private String answer;
    private QuestionType questionType;
    /** Canonical topic path joined with " › " (e.g. "함수 › 일차함수"). */
    private String topic;
    /** Same path split into 1..5 segments — convenient for filters / breadcrumb rendering. */
    private List<String> topicLevels;
    private String videoLink;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TextbookProblemDto from(TextbookProblem entity) {
        return TextbookProblemDto.builder()
                .id(entity.getId())
                .textbookId(entity.getTextbook() != null ? entity.getTextbook().getId() : null)
                .number(entity.getNumber())
                .answer(entity.getAnswer())
                .questionType(entity.getQuestionType())
                .topic(entity.getTopic())
                .topicLevels(collectLevels(entity))
                .videoLink(entity.getVideoLink())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static List<String> collectLevels(TextbookProblem e) {
        List<String> out = new ArrayList<>(5);
        String[] all = {e.getTopicL1(), e.getTopicL2(), e.getTopicL3(), e.getTopicL4(), e.getTopicL5()};
        for (String s : all) {
            if (s == null || s.isBlank()) break;
            out.add(s);
        }
        return out;
    }
}
