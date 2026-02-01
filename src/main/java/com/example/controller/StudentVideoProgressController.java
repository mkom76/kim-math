package com.example.controller;

import com.example.dto.StudentVideoProgressDto;
import com.example.service.StudentVideoProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StudentVideoProgressController {
    private final StudentVideoProgressService progressService;

    @PutMapping("/api/students/{studentId}/videos/{videoId}/progress")
    public ResponseEntity<StudentVideoProgressDto> updateProgress(
            @PathVariable Long studentId,
            @PathVariable Long videoId,
            @RequestBody Map<String, Integer> request
    ) {
        Integer currentTime = request.get("currentTime");
        Integer duration = request.get("duration");

        if (currentTime == null || duration == null) {
            return ResponseEntity.badRequest().build();
        }

        StudentVideoProgressDto progress = progressService.updateProgress(
                studentId, videoId, currentTime, duration
        );

        return ResponseEntity.ok(progress);
    }

    @GetMapping("/api/students/{studentId}/videos/progress")
    public ResponseEntity<List<StudentVideoProgressDto>> getStudentProgress(
            @PathVariable Long studentId
    ) {
        return ResponseEntity.ok(progressService.getStudentProgress(studentId));
    }
}
