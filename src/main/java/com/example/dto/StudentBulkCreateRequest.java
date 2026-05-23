package com.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class StudentBulkCreateRequest {

    @NotNull
    private Long classId;

    @NotEmpty
    @Valid
    private List<Item> students;

    @Data
    public static class Item {
        @NotBlank private String name;
        @NotBlank private String grade;
        @NotBlank private String school;
        @NotBlank private String parentName;

        /** Digits + optional dashes; 10–13 chars after normalization. */
        @NotBlank
        @Pattern(regexp = "^[0-9\\-]{10,20}$", message = "부모 휴대폰 형식이 올바르지 않습니다")
        private String parentPhone;

        /** Optional. Same shape as parentPhone if provided. */
        @Pattern(regexp = "^$|^[0-9\\-]{10,20}$", message = "학생 휴대폰 형식이 올바르지 않습니다")
        private String contactPhone;
    }
}
