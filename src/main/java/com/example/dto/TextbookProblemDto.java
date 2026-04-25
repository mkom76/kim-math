package com.example.dto;

import com.example.entity.QuestionType;
import com.example.entity.TextbookProblem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String topic;
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
                .videoLink(entity.getVideoLink())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
