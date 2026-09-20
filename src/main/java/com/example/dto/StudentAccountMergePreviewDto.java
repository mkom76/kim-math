package com.example.dto;

import java.util.List;

public record StudentAccountMergePreviewDto(
        StudentMergeStudentDto target,
        List<StudentMergeStudentDto> sources,
        List<StudentAccountMergeImpactDto> impacts,
        List<StudentAccountMergeConflictDto> conflicts,
        boolean mergeable) {
}
