package com.example.controller;

import com.example.dto.EssayGradeRequest;
import com.example.dto.StudentSubmissionDto;
import com.example.dto.SubmissionDetailDto;
import com.example.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService submissionService;
    
    @PostMapping
    public ResponseEntity<StudentSubmissionDto> submitAnswers(
            @RequestParam Long studentId,
            @RequestParam Long testId,
            @RequestBody Map<Integer, String> answers) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.submitAnswers(studentId, testId, answers));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<StudentSubmissionDto> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getSubmission(id));
    }
    
    @GetMapping
    public ResponseEntity<StudentSubmissionDto> getSubmissionByStudentAndTest(
            @RequestParam Long studentId,
            @RequestParam Long testId) {
        return ResponseEntity.ok(submissionService.getSubmissionByStudentAndTest(studentId, testId));
    }
    
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentSubmissionDto>> getStudentSubmissions(@PathVariable Long studentId) {
        return ResponseEntity.ok(submissionService.getStudentSubmissions(studentId));
    }

    @GetMapping("/test/{testId}")
    public ResponseEntity<List<StudentSubmissionDto>> getTestSubmissions(@PathVariable Long testId) {
        return ResponseEntity.ok(submissionService.getTestSubmissions(testId));
    }

    @PutMapping("/details/{detailId}/grade")
    public ResponseEntity<SubmissionDetailDto> gradeEssay(
            @PathVariable Long detailId,
            @RequestBody EssayGradeRequest request) {
        return ResponseEntity.ok(submissionService.gradeEssay(detailId, request));
    }
}