package com.example.controller;

import com.example.dto.AiFeedbackRequest;
import com.example.dto.AiFeedbackResponse;
import com.example.dto.BulkAiFeedbackRequest;
import com.example.dto.BulkAiFeedbackResponse;
import com.example.service.ai.AiFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-feedback")
@RequiredArgsConstructor
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/generate")
    public ResponseEntity<AiFeedbackResponse> generateFeedback(
            @RequestBody AiFeedbackRequest request) {
        return ResponseEntity.ok(aiFeedbackService.generateFeedback(request));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/generate-bulk")
    public ResponseEntity<BulkAiFeedbackResponse> generateBulkFeedback(
            @RequestBody BulkAiFeedbackRequest request) {
        return ResponseEntity.ok(aiFeedbackService.startBulkFeedback(request));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/generate-bulk/status/{lessonId}")
    public ResponseEntity<BulkAiFeedbackResponse> getBulkStatus(@PathVariable Long lessonId) {
        BulkAiFeedbackResponse status = aiFeedbackService.getBulkStatus(lessonId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
