package com.example.controller;

import com.example.dto.TextbookDto;
import com.example.dto.TextbookProblemDto;
import com.example.service.TextbookProblemService;
import com.example.service.TextbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TextbookController {

    private final TextbookService textbookService;
    private final TextbookProblemService textbookProblemService;

    // --- Textbook ---

    @GetMapping("/textbooks")
    public ResponseEntity<List<TextbookDto>> list() {
        return ResponseEntity.ok(textbookService.listMine());
    }

    @PostMapping("/textbooks")
    public ResponseEntity<TextbookDto> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(textbookService.create(body.get("title")));
    }

    @GetMapping("/textbooks/{id}")
    public ResponseEntity<TextbookDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(textbookService.get(id));
    }

    @PutMapping("/textbooks/{id}")
    public ResponseEntity<TextbookDto> update(@PathVariable Long id,
                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(textbookService.update(id, body.get("title")));
    }

    @DeleteMapping("/textbooks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        textbookService.delete(id);
        return ResponseEntity.ok().build();
    }

    // --- Textbook Problem ---

    @GetMapping("/textbooks/{textbookId}/problems")
    public ResponseEntity<List<TextbookProblemDto>> listProblems(@PathVariable Long textbookId) {
        return ResponseEntity.ok(textbookProblemService.listByTextbook(textbookId));
    }

    @PostMapping("/textbooks/{textbookId}/problems")
    public ResponseEntity<TextbookProblemDto> createProblem(@PathVariable Long textbookId,
                                                            @RequestBody TextbookProblemDto dto) {
        return ResponseEntity.ok(textbookProblemService.create(textbookId, dto));
    }

    @PostMapping("/textbooks/{textbookId}/problems/bulk")
    public ResponseEntity<List<TextbookProblemDto>> bulkCreateProblems(@PathVariable Long textbookId,
                                                                       @RequestBody List<TextbookProblemDto> items) {
        return ResponseEntity.ok(textbookProblemService.bulkCreate(textbookId, items));
    }

    @PutMapping("/textbook-problems/{id}")
    public ResponseEntity<TextbookProblemDto> updateProblem(@PathVariable Long id,
                                                            @RequestBody TextbookProblemDto dto) {
        return ResponseEntity.ok(textbookProblemService.update(id, dto));
    }

    @DeleteMapping("/textbook-problems/{id}")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        textbookProblemService.delete(id);
        return ResponseEntity.ok().build();
    }
}
