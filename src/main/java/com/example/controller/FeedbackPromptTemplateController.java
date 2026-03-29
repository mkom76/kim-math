package com.example.controller;

import com.example.dto.FeedbackPromptTemplateDto;
import com.example.service.FeedbackPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback-prompt-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class FeedbackPromptTemplateController {

    private final FeedbackPromptTemplateService service;

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<FeedbackPromptTemplateDto> getByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(service.getByTeacherId(teacherId));
    }

    @PutMapping("/teacher/{teacherId}")
    public ResponseEntity<FeedbackPromptTemplateDto> save(
            @PathVariable Long teacherId,
            @RequestBody FeedbackPromptTemplateDto dto) {
        dto.setTeacherId(teacherId);
        return ResponseEntity.ok(service.save(dto));
    }
}
