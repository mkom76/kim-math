package com.example.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiFeedbackResponse {
    private String generatedFeedback;
    private Long studentId;
    private Long lessonId;
}
