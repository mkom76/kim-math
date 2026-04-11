package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicDetailDto {
    private ClinicDto clinic;
    private List<StudentClinicHomeworkDto> students;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentClinicHomeworkDto {
        private Long studentId;
        private String studentName;
        private ClinicRegistrationDto registration; // null if not registered
        private List<HomeworkProgressDto> homeworks; // 완성도가 낮은 숙제 목록
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeworkProgressDto {
        private Long homeworkId;
        private String homeworkTitle;
        private Integer questionCount;
        private Integer incorrectCount;
        private Integer unsolvedCount;
        private String incorrectQuestions; // 오답 문항번호 (CSV)
        private String unsolvedQuestions;  // 안 푼 문항번호 (CSV)
        private Integer completion;
        private Long lessonId;
        private String lessonDate;
        private Boolean followUpFlag;
    }
}
