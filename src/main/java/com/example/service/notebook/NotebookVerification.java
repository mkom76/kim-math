package com.example.service.notebook;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Bounded, transient feedback for the job owner; never logged or stored separately. */
public final class NotebookVerification {
    private NotebookVerification() {}
    public enum Stage { MATH, IMAGE }
    public enum Verdict { PASSED, MISMATCH, UNCERTAIN, INVALID_RESPONSE, UNAVAILABLE }
    public enum Category { SIGN, FRACTION, EXPONENT_ROOT, CONDITION, CALCULATION, ANSWER,
        OMISSION, EXTRA_CONTENT, DIAGRAM, UNREADABLE, OTHER }
    public record Issue(Category category, String location, String expected, String observed, String reason) {}
    public record Report(Stage stage, Verdict verdict, List<Issue> issues) {
        public Report { issues = List.copyOf(issues); }
        public boolean passed() { return verdict == Verdict.PASSED && issues.isEmpty(); }
    }
    public static Report empty(Stage stage, Verdict verdict) { return new Report(stage, verdict, List.of()); }
    public static Report legacy(Stage stage, boolean passed) {
        return empty(stage, passed ? Verdict.PASSED : Verdict.UNCERTAIN);
    }
    public static Report parse(Stage stage, JsonNode node) {
        try {
            if (node == null || !node.isObject() || !node.path("verdict").isTextual()
                    || !node.path("issues").isArray() || node.path("issues").size() > 5)
                return empty(stage, Verdict.INVALID_RESPONSE);
            Verdict verdict = Verdict.valueOf(node.path("verdict").textValue());
            if (verdict != Verdict.PASSED && verdict != Verdict.MISMATCH && verdict != Verdict.UNCERTAIN)
                return empty(stage, Verdict.INVALID_RESPONSE);
            var issues = new ArrayList<Issue>();
            for (JsonNode issue : node.path("issues")) {
                if (!issue.path("category").isTextual()) throw new IllegalArgumentException();
                issues.add(new Issue(Category.valueOf(issue.path("category").textValue()),
                        field(issue, "location", 160, true), field(issue, "expected", 400, false),
                        field(issue, "observed", 400, false), field(issue, "reason", 500, true)));
            }
            // Missing explanations or a contradictory pass must never release an unchecked image.
            if (verdict == Verdict.PASSED && !issues.isEmpty() || verdict != Verdict.PASSED && issues.isEmpty())
                return empty(stage, Verdict.INVALID_RESPONSE);
            return new Report(stage, verdict, issues);
        } catch (IllegalArgumentException e) { return empty(stage, Verdict.INVALID_RESPONSE); }
    }
    private static String field(JsonNode node, String name, int limit, boolean required) {
        JsonNode value = node.path(name);
        if (!value.isTextual()) throw new IllegalArgumentException();
        String text = value.textValue().strip();
        if (text.length() > limit || required && text.isBlank()
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw new IllegalArgumentException();
        return text;
    }
}
