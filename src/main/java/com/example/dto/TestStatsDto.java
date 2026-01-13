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
public class TestStatsDto {
    private Long testId;
    private String testTitle;
    private Double averageScore;
    private Integer maxScore;
    private List<StudentScore> studentScores;
    private List<QuestionStat> questionStats;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentScore {
        private Long studentId;
        private String studentName;
        private Integer totalScore;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionStat {
        private Integer questionNumber;
        private Double correctRate;
        private List<String> incorrectStudents; // 틀린 학생 이름 목록
    }
}