package com.example.dto;

import com.example.entity.StudentUiFeedback;

import java.time.LocalDateTime;

public record StudentUiFeedbackResponse(Long id, LocalDateTime createdAt) {
    public static StudentUiFeedbackResponse from(StudentUiFeedback feedback) {
        return new StudentUiFeedbackResponse(feedback.getId(), feedback.getCreatedAt());
    }
}
