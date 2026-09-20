package com.example.dto;

public record StudentAccountMergeConflictDto(
        String key,
        String label,
        long conflictCount) {
}
