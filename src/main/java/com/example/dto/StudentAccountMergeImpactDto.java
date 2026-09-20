package com.example.dto;

public record StudentAccountMergeImpactDto(
        String key,
        String label,
        long count,
        String action) {
}
