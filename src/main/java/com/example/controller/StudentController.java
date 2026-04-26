package com.example.controller;

import com.example.dto.StudentDto;
import com.example.service.StudentService;
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
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public ResponseEntity<Page<StudentDto>> getStudents(
            @RequestParam(required = false) String name,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(studentService.getStudents(name, pageable));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<StudentDto> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }
    
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public ResponseEntity<StudentDto> createStudent(@RequestBody StudentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studentService.createStudent(dto));
    }
    
    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentDto> updateStudent(
            @PathVariable Long id, 
            @RequestBody StudentDto dto) {
        return ResponseEntity.ok(studentService.updateStudent(id, dto));
    }
    
    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{id}/reset-pin")
    public ResponseEntity<StudentDto> resetStudentPin(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> request) {
        String newPin = request.get("pin");
        if (newPin == null || newPin.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(studentService.resetPin(id, newPin));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{id}/score-visibility")
    public ResponseEntity<StudentDto> setScoreVisibility(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        Boolean hide = request.get("hide");
        if (hide == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(studentService.setScoreVisibility(id, hide));
    }
}