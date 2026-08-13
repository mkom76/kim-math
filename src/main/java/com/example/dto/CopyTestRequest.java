package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CopyTestRequest(
        @NotNull(message = "대상 반을 선택해주세요") Long targetClassId,
        @NotBlank(message = "시험명을 입력해주세요") String title
) {
}
