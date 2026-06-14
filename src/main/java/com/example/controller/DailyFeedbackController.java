package com.example.controller;

import com.example.dto.DailyFeedbackDto;
import com.example.dto.StudentLessonDto;
import com.example.service.DailyFeedbackService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-feedback")
@RequiredArgsConstructor
public class DailyFeedbackController {
    private final DailyFeedbackService dailyFeedbackService;

    @GetMapping("/student/{studentId}/lesson/{lessonId}")
    public ResponseEntity<DailyFeedbackDto> getDailyFeedback(
            @PathVariable Long studentId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(dailyFeedbackService.getDailyFeedback(studentId, lessonId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/student/{studentId}/lesson/{lessonId}")
    public ResponseEntity<StudentLessonDto> updateInstructorFeedback(
            @PathVariable Long studentId,
            @PathVariable Long lessonId,
            @RequestBody UpdateFeedbackRequest request) {
        return ResponseEntity.ok(dailyFeedbackService.updateInstructorFeedback(
            studentId, lessonId, request.getFeedback(), request.getAuthorName(),
            Boolean.TRUE.equals(request.getIsAiFeedback())));
    }

    /**
     * Teacher action: notify every student in this lesson who has a written
     * feedback. Returns how many students were notified.
     */
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/lesson/{lessonId}/notify")
    public ResponseEntity<NotifyResponse> notifyLessonFeedback(@PathVariable Long lessonId) {
        int count = dailyFeedbackService.notifyLessonFeedback(lessonId);
        return ResponseEntity.ok(new NotifyResponse(count));
    }

    @Data
    public static class UpdateFeedbackRequest {
        private String feedback;
        private String authorName;
        private Boolean isAiFeedback;
    }

    @Data
    @AllArgsConstructor
    public static class NotifyResponse {
        private int sentCount;
    }
}
