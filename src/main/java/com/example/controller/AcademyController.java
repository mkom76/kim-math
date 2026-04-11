package com.example.controller;

import com.example.dto.AcademyDto;
import com.example.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 학원 row 자체의 생성/삭제는 제공하지 않는다. 신규 학원 셋업은 개발자가
 * DB로 직접 처리한다 — migrations/setup-new-academy.sql 참고.
 */
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

    @PutMapping("/{id}")
    public ResponseEntity<AcademyDto> updateAcademy(
            @PathVariable Long id,
            @RequestBody AcademyDto dto) {
        return ResponseEntity.ok(academyService.updateAcademy(id, dto));
    }
}
