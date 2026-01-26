package com.example.controller;

import com.example.dto.TestAnswersDto;
import com.example.dto.TestDto;
import com.example.dto.TestQuestionDto;
import com.example.dto.TestStatsDto;
import com.example.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {
    private final TestService testService;
    
    @GetMapping
    public ResponseEntity<Page<TestDto>> getTests(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(testService.getTests(pageable));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TestDto> getTest(@PathVariable Long id) {
        return ResponseEntity.ok(testService.getTest(id));
    }
    
    @PostMapping
    public ResponseEntity<TestDto> createTest(@RequestBody TestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(testService.createTest(dto));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TestDto> updateTest(
            @PathVariable Long id, 
            @RequestBody TestDto dto) {
        return ResponseEntity.ok(testService.updateTest(id, dto));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTest(@PathVariable Long id) {
        testService.deleteTest(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/stats")
    public ResponseEntity<TestStatsDto> getTestStats(@PathVariable Long id) {
        return ResponseEntity.ok(testService.getTestStats(id));
    }
    
    @GetMapping("/{id}/questions")
    public ResponseEntity<List<TestQuestionDto>> getTestQuestions(@PathVariable Long id) {
        return ResponseEntity.ok(testService.getTestQuestions(id));
    }
    
    @PutMapping("/{id}/answers")
    public ResponseEntity<Void> saveTestAnswers(
            @PathVariable Long id,
            @RequestBody TestAnswersDto dto) {
        dto.setTestId(id);
        testService.saveTestAnswers(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<TestQuestionDto> addQuestion(
            @PathVariable Long id,
            @RequestBody TestQuestionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(testService.addQuestion(id, dto));
    }

    @GetMapping("/unattached")
    public ResponseEntity<List<TestDto>> getUnattachedTests(
            @RequestParam Long academyId,
            @RequestParam Long classId) {
        return ResponseEntity.ok(testService.getUnattachedTests(academyId, classId));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<Void> recalculateTestScores(@PathVariable Long id) {
        testService.recalculateScores(id);
        return ResponseEntity.ok().build();
    }

}