package com.example.dto;

import lombok.Data;

@Data
public class BulkAiFeedbackRequest {
    private Long lessonId;
    private Long teacherId;
    private String model;
}
