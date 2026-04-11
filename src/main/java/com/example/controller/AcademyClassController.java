package com.example.controller;

import com.example.dto.AcademyClassDto;
import com.example.service.AcademyClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class AcademyClassController {
    private final AcademyClassService academyClassService;

    @GetMapping
    public ResponseEntity<Page<AcademyClassDto>> getClasses(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(academyClassService.getClasses(pageable));
    }

    @PostMapping
    public ResponseEntity<AcademyClassDto> createClass(@RequestBody AcademyClassDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academyClassService.createClass(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcademyClassDto> updateClass(
            @PathVariable Long id,
            @RequestBody AcademyClassDto dto) {
        return ResponseEntity.ok(academyClassService.updateClass(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClass(@PathVariable Long id) {
        academyClassService.deleteClass(id);
        return ResponseEntity.noContent().build();
    }
}
