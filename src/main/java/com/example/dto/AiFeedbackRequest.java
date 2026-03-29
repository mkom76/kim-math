package com.example.dto;

import lombok.Data;

@Data
public class AiFeedbackRequest {
    private Long studentId;
    private Long lessonId;
    private Long teacherId;
}
