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
public class LessonStudentStatsDto {
    private List<StudentTestScore> testScores;
    private Double testAverage;
    private List<StudentHomeworkCompletion> homeworkCompletions;
    private Double homeworkAverage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentTestScore {
        private Long studentId;
        private String studentName;
        private Integer score;
        private Integer rank;
        private Boolean submitted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentHomeworkCompletion {
        private Long studentId;
        private String studentName;
        private Integer incorrectCount;
        private Integer unsolvedCount;
        private String incorrectQuestions;
        private String unsolvedQuestions;
        private Integer completion;
        private Boolean completed;
        private Integer totalQuestions;
    }
}
