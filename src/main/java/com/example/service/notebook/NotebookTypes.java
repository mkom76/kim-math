package com.example.service.notebook;

import java.time.Instant;
import java.util.List;

public final class NotebookTypes {
    private NotebookTypes() {}
    public static final String FIXTURE = "rational-asymptote";
    public static final String QUESTION = "유리함수 f(x)=(2x+1)/(x−1)의 그래프를 그리고, 두 점근선의 교점을 구하시오.";
    public enum Scenario { SUCCESS, UNREADABLE, TIMEOUT, RATE_LIMIT, MATH_FAILED, IMAGE_FAILED }
    public enum State { QUEUED, READING, SOLVING, VERIFYING, RENDERING, CHECKING_IMAGE, SUCCEEDED, FAILED, REVIEW_REQUIRED }
    public enum Kind { READ, SOLUTION }
    // Built only from server-side authentication/session state, never request JSON.
    public record Owner(String role, Long userId, Long academyId, Long classId, String sessionId, String effectiveRole) {}
    public record ReadRequest(String fixture, Scenario scenario) {}
    public record SolveRequest(String readJobId, String question, boolean confirmed, String grade, String depth, Scenario scenario) {}
    public record Solution(String title, List<String> steps, String answer, String diagram) {}
    public record JobView(String id, Kind kind, State state, String mode, String question,
                          List<String> uncertainties, Solution solution, boolean imageAvailable,
                          boolean verified, String message, Instant expiresAt, NotebookVerification.Report verification) {
        public JobView(String id, Kind kind, State state, String mode, String question,
                       List<String> uncertainties, Solution solution, boolean imageAvailable,
                       boolean verified, String message, Instant expiresAt) {
            this(id, kind, state, mode, question, uncertainties, solution, imageAvailable, verified, message, expiresAt, null);
        }
    }
    public record Configuration(boolean enabled, String mode, String fixture, String sampleQuestion,
                                int dailySolutionLimit, int readLimit, int retentionMinutes,
                                String notice) {}
}
