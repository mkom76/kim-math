package com.example.dto;

import com.example.entity.StudentClassEnrollmentStatus;

public record StudentMergeEnrollmentDto(
        Long classId,
        String className,
        StudentClassEnrollmentStatus status) {
}
