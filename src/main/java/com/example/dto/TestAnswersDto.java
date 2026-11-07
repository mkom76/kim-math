package com.example.dto;

import com.example.entity.QuestionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestAnswersDto {
    private Long testId;
    private List<Long> expectedQuestionIds;
    private List<QuestionAnswer> answers;
    
    public record QuestionAnswer(
            // null은 새 문항이며, 필드 자체가 없는 예전 번호 기반 요청은 거부한다.
            @JsonProperty(value = "id", required = true) Long id,
            Integer number,
            String answer,
            Boolean multipleAnswers,
            Double points,
            QuestionType questionType,
            String topic
    ) {
    }
}
