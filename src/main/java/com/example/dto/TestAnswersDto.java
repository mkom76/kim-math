package com.example.dto;

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
    private List<QuestionAnswer> answers;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionAnswer {
        private Integer number;
        private String answer;
        private Double points;
    }
}