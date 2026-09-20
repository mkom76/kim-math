package com.example.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StudentAccountMergeRequest(
        @NotNull Long targetStudentId,
        @NotEmpty List<@NotNull Long> sourceStudentIds) {
}
