package com.example.entity;

/**
 * 시험 문제 유형
 */
public enum QuestionType {
    OBJECTIVE("객관식"),
    SUBJECTIVE("주관식"),
    ESSAY("서술형");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
