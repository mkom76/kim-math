package com.example.service;

import com.example.dto.StudentUiFeedbackRequest;
import com.example.dto.StudentUiFeedbackResponse;
import com.example.entity.Student;
import com.example.entity.StudentUiFeedback;
import com.example.repository.StudentRepository;
import com.example.repository.StudentUiFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentUiFeedbackService {
    private static final Set<String> SENTIMENTS = Set.of("POSITIVE", "IMPROVEMENT", "BUG");
    private static final Set<String> CATEGORIES = Set.of(
            "NAVIGATION", "LAYOUT", "READABILITY", "PERFORMANCE", "CONTENT", "OTHER");
    private static final int HOURLY_SUBMISSION_LIMIT = 20;

    private final StudentUiFeedbackRepository feedbackRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public StudentUiFeedbackResponse create(Long studentId, StudentUiFeedbackRequest request) {
        String sentiment = normalizeRequired(request.sentiment());
        String category = normalizeOptional(request.category());
        String message = normalizeOptional(request.message());
        String pagePath = normalizeRequired(request.pagePath());
        String uiVersion = normalizeRequired(request.uiVersion());
        String platform = normalizeOptional(request.platform());
        String appVersion = normalizeOptional(request.appVersion());

        if (!SENTIMENTS.contains(sentiment)) {
            throw new IllegalArgumentException("올바른 만족도를 선택해주세요");
        }
        if (category != null && !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("올바른 불편 유형을 선택해주세요");
        }
        if (!"POSITIVE".equals(sentiment) && category == null) {
            throw new IllegalArgumentException("불편했던 부분을 선택해주세요");
        }
        if ("POSITIVE".equals(sentiment)) {
            category = null;
        }
        if (!pagePath.equals("/student") && !pagePath.startsWith("/student/") && !pagePath.equals("/settings")) {
            throw new IllegalArgumentException("새 학생 UI 화면에서만 의견을 보낼 수 있습니다");
        }
        if (!"v2".equalsIgnoreCase(uiVersion)) {
            throw new IllegalArgumentException("지원하지 않는 UI 버전입니다");
        }
        if (feedbackRepository.countByStudentIdAndCreatedAtAfter(
                studentId, LocalDateTime.now().minusHours(1)) >= HOURLY_SUBMISSION_LIMIT) {
            throw new IllegalArgumentException("의견을 너무 자주 보내고 있어요. 잠시 후 다시 시도해주세요");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다"));

        StudentUiFeedback saved = feedbackRepository.save(StudentUiFeedback.builder()
                .studentId(studentId)
                .academyId(student.getAcademy().getId())
                .sentiment(sentiment)
                .category(category)
                .message(message)
                .pagePath(pagePath)
                .uiVersion("v2")
                .viewportWidth(request.viewportWidth())
                .platform(platform)
                .appVersion(appVersion)
                .build());
        return StudentUiFeedbackResponse.from(saved);
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
