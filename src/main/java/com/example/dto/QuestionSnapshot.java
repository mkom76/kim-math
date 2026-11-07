package com.example.dto;

import com.example.entity.TestQuestion;

import java.time.LocalDateTime;

public record QuestionSnapshot(Long id, Integer number, LocalDateTime updatedAt) {
    public static QuestionSnapshot from(TestQuestion question) {
        return new QuestionSnapshot(question.getId(), question.getNumber(), question.getUpdatedAt());
    }
}
