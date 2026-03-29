package com.example.controller;

import com.example.dto.AiFeedbackRequest;
import com.example.dto.AiFeedbackResponse;
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
}
