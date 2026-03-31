package com.example.controller;

import com.example.dto.StudentHomeworkDto;
import com.example.service.StudentHomeworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student-homeworks")
@RequiredArgsConstructor
public class StudentHomeworkController {
    private final StudentHomeworkService studentHomeworkService;

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentHomeworkDto>> getByStudentId(@PathVariable Long studentId) {
        return ResponseEntity.ok(studentHomeworkService.getByStudentId(studentId));
    }

    @GetMapping("/homework/{homeworkId}")
    public ResponseEntity<List<StudentHomeworkDto>> getByHomeworkId(@PathVariable Long homeworkId) {
        return ResponseEntity.ok(studentHomeworkService.getByHomeworkId(homeworkId));
    }

    @PutMapping("/student/{studentId}/homework/{homeworkId}")
    public ResponseEntity<StudentHomeworkDto> updateIncorrectCount(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId,
            @RequestBody Map<String, Object> request) {
        Integer incorrectCount = request.get("incorrectCount") != null ? ((Number) request.get("incorrectCount")).intValue() : null;
        Integer unsolvedCount = request.get("unsolvedCount") != null ? ((Number) request.get("unsolvedCount")).intValue() : null;
        String incorrectQuestions = (String) request.get("incorrectQuestions");
        String unsolvedQuestions = (String) request.get("unsolvedQuestions");
        return ResponseEntity.ok(studentHomeworkService.updateIncorrectCount(studentId, homeworkId, incorrectCount, unsolvedCount, incorrectQuestions, unsolvedQuestions));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/student/{studentId}/homework/{homeworkId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        studentHomeworkService.deleteByStudentIdAndHomeworkId(studentId, homeworkId);
        return ResponseEntity.noContent().build();
    }
}
