package com.example.dto;

import com.example.entity.Test;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestDto {
    private Long id;
    private String title;
    private Long academyId;
    private String academyName;
    private Long classId;
    private String className;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer questionCount;
    private String pdfImportId;

    public static TestDto from(Test test) {
        return TestDto.builder()
                .id(test.getId())
                .title(test.getTitle())
                .academyId(test.getAcademy() != null ? test.getAcademy().getId() : null)
                .academyName(test.getAcademy() != null ? test.getAcademy().getName() : null)
                .classId(test.getAcademyClass() != null ? test.getAcademyClass().getId() : null)
                .className(test.getAcademyClass() != null ? test.getAcademyClass().getName() : null)
                .createdAt(test.getCreatedAt())
                .updatedAt(test.getUpdatedAt())
                .questionCount(test.getQuestions() != null ? test.getQuestions().size() : 0)
                .build();
    }
}
