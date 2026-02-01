package com.example.controller;

import com.example.dto.StudentLessonVideosDto;
import com.example.service.LessonVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students/{studentId}/videos")
@RequiredArgsConstructor
public class StudentVideoController {
    private final LessonVideoService lessonVideoService;

    @GetMapping
    public ResponseEntity<List<StudentLessonVideosDto>> getStudentVideos(@PathVariable Long studentId) {
        return ResponseEntity.ok(lessonVideoService.getStudentVideos(studentId));
    }
}
