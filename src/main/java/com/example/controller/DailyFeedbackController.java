package com.example.controller;

import com.example.dto.DailyFeedbackDto;
import com.example.dto.StudentLessonDto;
import com.example.service.DailyFeedbackService;
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

    @GetMapping("/student/{studentId}/today")
    public ResponseEntity<DailyFeedbackDto> getTodayFeedback(@PathVariable Long studentId) {
        return ResponseEntity.ok(dailyFeedbackService.getTodayFeedback(studentId));
    }

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
            studentId, lessonId, request.getFeedback(), request.getAuthorName()));
    }

    @Data
    public static class UpdateFeedbackRequest {
        private String feedback;
        private String authorName;
    }
}
