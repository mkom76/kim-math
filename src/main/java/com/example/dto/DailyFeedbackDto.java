package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyFeedbackDto {
    private Long lessonId;
    private LocalDate lessonDate;
    private HomeworkSummary todayHomework;
    private HomeworkSummary nextHomework;
    private TestFeedback todayTest;
    private String instructorFeedback;
    private String feedbackAuthor;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeworkSummary {
        private Long homeworkId;
        private String homeworkTitle;
        private Integer questionCount;
        private Integer incorrectCount; // 오답 개수
        private Integer unsolvedCount; // 안 푼 문제 개수
        private Integer completion; // 0-100
        private LocalDate dueDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TestFeedback {
        private Long testId;
        private String testTitle;
        private Integer studentScore;
        private Double classAverage;
        private Integer rank;
        private List<Integer> incorrectQuestions;
        private List<QuestionAccuracy> questionAccuracyRates;
        private List<EssayDetail> essayDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionAccuracy {
        private Integer questionNumber;
        private Double correctRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EssayDetail {
        private Integer questionNumber;
        private Double maxPoints;
        private String studentAnswer;
        private Double earnedPoints;   // null = 미채점
        private String teacherComment; // null = 코멘트 없음
    }
}
