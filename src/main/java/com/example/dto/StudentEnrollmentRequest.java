package com.example.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class StudentEnrollmentRequest {
    @NotNull
    @Positive
    private Long classId;
}
