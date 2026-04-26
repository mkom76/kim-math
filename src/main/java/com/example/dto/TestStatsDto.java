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
        private String questionType;            // OBJECTIVE / SUBJECTIVE / ESSAY
        private Double correctRate;             // OBJECTIVE/SUBJECTIVE: 정답률 (0-100)
        private Double avgEarnedRate;           // ESSAY: 평균 획득률 (0-100, null = 아직 미채점)
        private List<String> incorrectStudents; // OBJECTIVE/SUBJECTIVE: 틀린 학생, ESSAY: 미채점 학생
        private String topic;                   // 교재 문제에서 라이브 메타
        private String videoLink;               // 교재 문제에서 라이브 메타
    }
}