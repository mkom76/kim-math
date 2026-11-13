package com.example.dto;

import com.example.entity.Homework;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeworkDto {
    private Long id;
    private String title;
    private Integer questionCount;
    private String pdfImportId;
    private String memo;
    private LocalDate dueDate;
    private Long academyId;
    private String academyName;
    private Long classId;
    private String className;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static HomeworkDto from(Homework homework) {
        return HomeworkDto.builder()
                .id(homework.getId())
                .title(homework.getTitle())
                .questionCount(homework.getQuestionCount())
                .memo(homework.getMemo())
                .dueDate(homework.getDueDate())
                .academyId(homework.getAcademy() != null ? homework.getAcademy().getId() : null)
                .academyName(homework.getAcademy() != null ? homework.getAcademy().getName() : null)
                .classId(homework.getAcademyClass() != null ? homework.getAcademyClass().getId() : null)
                .className(homework.getAcademyClass() != null ? homework.getAcademyClass().getName() : null)
                .createdAt(homework.getCreatedAt())
                .updatedAt(homework.getUpdatedAt())
                .build();
    }
}
