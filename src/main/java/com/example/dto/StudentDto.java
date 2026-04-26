package com.example.dto;

import com.example.entity.Student;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDto {
    private Long id;
    private String name;
    private String grade;
    private String school;
    private Long academyId;
    private String academyName;
    private Long classId;
    private String className;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String pin;
    private Boolean hideScoresFromStudent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentDto from(Student student) {
        return StudentDto.builder()
                .id(student.getId())
                .name(student.getName())
                .grade(student.getGrade())
                .school(student.getSchool())
                .academyId(student.getAcademy() != null ? student.getAcademy().getId() : null)
                .academyName(student.getAcademy() != null ? student.getAcademy().getName() : null)
                .classId(student.getAcademyClass() != null ? student.getAcademyClass().getId() : null)
                .className(student.getAcademyClass() != null ? student.getAcademyClass().getName() : null)
                .hideScoresFromStudent(Boolean.TRUE.equals(student.getHideScoresFromStudent()))
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }
}