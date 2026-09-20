package com.example.dto;

import java.util.List;

public record StudentAccountMergeCandidateDto(
        List<StudentMergeStudentDto> students,
        List<String> matchReasons) {
}
