package com.example.controller;

import com.example.dto.StudentAccountMergeCandidateDto;
import com.example.dto.StudentAccountMergePreviewDto;
import com.example.dto.StudentAccountMergeRequest;
import com.example.dto.StudentAccountMergeResultDto;
import com.example.service.StudentAccountMergeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/student-account-merges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class AdminStudentAccountMergeController {
    private final StudentAccountMergeService mergeService;

    @GetMapping("/candidates")
    public List<StudentAccountMergeCandidateDto> getCandidates() {
        return mergeService.getCandidates();
    }

    @PostMapping("/preview")
    public StudentAccountMergePreviewDto preview(
            @Valid @RequestBody StudentAccountMergeRequest request) {
        return mergeService.preview(request);
    }

    @PostMapping
    public StudentAccountMergeResultDto merge(
            @Valid @RequestBody StudentAccountMergeRequest request) {
        return mergeService.merge(request);
    }
}
