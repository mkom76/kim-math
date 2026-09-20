package com.example.dto;

import com.example.entity.StudentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StudentMergeStudentDto(
        Long id,
        String name,
        String grade,
        String school,
        String parentName,
        String parentPhoneMasked,
        String contactPhoneMasked,
        StudentStatus status,
        List<StudentMergeEnrollmentDto> enrollments,
        LocalDateTime createdAt) {
}
