package com.example.controller;

import com.example.dto.HomeworkDto;
import com.example.service.HomeworkService;
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
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
public class HomeworkController {
    private final HomeworkService homeworkService;

    @GetMapping
    public ResponseEntity<Page<HomeworkDto>> getHomeworks(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(homeworkService.getHomeworks(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HomeworkDto> getHomework(@PathVariable Long id) {
        return ResponseEntity.ok(homeworkService.getHomework(id));
    }

    @PostMapping
    public ResponseEntity<HomeworkDto> createHomework(@RequestBody HomeworkDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(homeworkService.createHomework(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HomeworkDto> updateHomework(
            @PathVariable Long id,
            @RequestBody HomeworkDto dto) {
        return ResponseEntity.ok(homeworkService.updateHomework(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHomework(@PathVariable Long id) {
        homeworkService.deleteHomework(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unattached")
    public ResponseEntity<List<HomeworkDto>> getUnattachedHomeworks(
            @RequestParam Long academyId,
            @RequestParam Long classId) {
        return ResponseEntity.ok(homeworkService.getUnattachedHomeworks(academyId, classId));
    }
}
