package com.example.dto;

import java.util.List;

public record StudentAccountMergeResultDto(
        Long targetStudentId,
        List<Long> mergedSourceStudentIds,
        List<StudentAccountMergeImpactDto> impacts) {
}
