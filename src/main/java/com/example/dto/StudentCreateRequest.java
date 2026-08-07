package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StudentCreateRequest {
    @NotBlank private String name;
    @NotBlank private String grade;
    @NotBlank private String school;
    private Long academyId;
    @NotNull private Long classId;
    @NotBlank private String parentName;

    @NotBlank
    @Pattern(regexp = "^[0-9\\-]{10,20}$", message = "보호자 휴대폰 형식이 올바르지 않습니다")
    private String parentPhone;

    @Pattern(regexp = "^$|^[0-9\\-]{10,20}$", message = "학생 휴대폰 형식이 올바르지 않습니다")
    private String contactPhone;
}
