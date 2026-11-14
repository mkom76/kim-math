package com.example.service.omr;

import com.example.dto.QuestionSnapshot;
import java.util.List;
import java.util.Map;

public final class OmrTypes {
    private OmrTypes() {}

    public record Box(double x, double y, double width, double height) {}
    public record Mark(int number, String answer, String status, Box box) {}
    public record Page(int number, boolean recognized, String message, String preview, List<Mark> marks) {}
    public record Question(int number, boolean multipleAnswers) {}
    public record Scan(List<QuestionSnapshot> expectedQuestions, List<Question> questions, List<Page> pages) {}
    public record Confirm(Map<Integer, String> answers, List<QuestionSnapshot> expectedQuestions, boolean reviewed) {}
    public record ObjectiveAnswer(int number, String answer, String correctAnswer, boolean multipleAnswers, double points) {}
    public record Review(String revision, boolean existing, Integer previousScore, double preservedPoints,
                         double maxPoints, int preservedQuestionCount, List<ObjectiveAnswer> objectives,
                         List<QuestionSnapshot> expectedQuestions) {}
    public record Update(Map<Integer, String> answers, List<QuestionSnapshot> expectedQuestions,
                         String expectedRevision, boolean reviewed) {}
}
