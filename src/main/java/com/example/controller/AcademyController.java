package com.example.controller;

import com.example.dto.AcademyDto;
import com.example.service.AcademyService;
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
@RequestMapping("/api/academies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class AcademyController {
    private final AcademyService academyService;

    @GetMapping
    public ResponseEntity<Page<AcademyDto>> getAcademies(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(academyService.getAcademies(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcademyDto> getAcademy(@PathVariable Long id) {
        return ResponseEntity.ok(academyService.getAcademy(id));
    }

    @PostMapping
    public ResponseEntity<AcademyDto> createAcademy(@RequestBody AcademyDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academyService.createAcademy(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcademyDto> updateAcademy(
            @PathVariable Long id,
            @RequestBody AcademyDto dto) {
        return ResponseEntity.ok(academyService.updateAcademy(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAcademy(@PathVariable Long id) {
        academyService.deleteAcademy(id);
        return ResponseEntity.noContent().build();
    }
}
