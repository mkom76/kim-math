package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackPromptTemplateDto {
    private Long id;
    private Long teacherId;
    private String systemPrompt;
    private Integer fewShotCount;
    private Boolean isActive;
}
