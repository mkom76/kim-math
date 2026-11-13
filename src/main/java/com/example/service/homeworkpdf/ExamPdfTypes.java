package com.example.service.homeworkpdf;

import java.time.LocalDateTime;
import java.util.List;

public final class ExamPdfTypes {
    private ExamPdfTypes() {}
    public record Region(int page, double x, double y, double width, double height) {}
    public record DetectedQuestion(String number, String text, List<Region> regions, String typeId, String reason, boolean needsReview, String answer, Double points, String format, boolean multipleAnswers) {}
    public record Analysis(boolean complete, List<DetectedQuestion> questions) {}
    public record Question(String id, int position, String sourceNumber, String text, List<Region> regions,
                           String suggestedTypeId, String confirmedTypeId, List<String> typePath,
                           String reason, boolean needsReview, String answer, Double points, String format, boolean multipleAnswers) {}
    public record View(String id, String state, Long version, Long testId, Long classId, String subjectId,
                       String fileName, int pageCount, int questionPageCount, String catalogVersion, boolean reviewRequired,
                       LocalDateTime expiresAt, String message, List<Question> questions) {}
    public record ConfirmQuestion(String id, String sourceNumber, String text, List<Region> regions, String typeId, String answer, Double points, String format, boolean multipleAnswers) {}
    public record Register(Long expectedVersion, String catalogVersion, String title,
                           boolean contentsReviewed, List<ConfirmQuestion> questions) {}
    public record Config(boolean enabled, boolean ready, boolean mock, int maxBytes, int maxPages, int maxQuestions,
                         String catalogVersion, String notice) {}
}
