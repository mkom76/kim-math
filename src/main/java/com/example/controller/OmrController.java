package com.example.controller;

import com.example.dto.SaveAnswersRequest;
import com.example.dto.StudentSubmissionDto;
import com.example.service.SubmissionService;
import com.example.service.omr.OmrService;
import com.example.service.omr.OmrTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/api/tests/{testId}/omr")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER') and !hasRole('ASSISTANT')")
public class OmrController {
    private final OmrService service;
    private final SubmissionService submissionService;

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OmrTypes.Scan> scan(@PathVariable Long testId, @RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > OmrService.MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "8MB 이하의 스캔 PDF를 선택해 주세요.");
        byte[] bytes = file.getBytes();
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.scan(testId, bytes)); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    @PostMapping("/submissions/{studentId}")
    public ResponseEntity<StudentSubmissionDto> confirm(@PathVariable Long testId, @PathVariable Long studentId, @RequestBody OmrTypes.Confirm request) {
        if (request == null || !request.reviewed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학생과 모든 답안을 확인한 뒤 확정해 주세요.");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(
                submissionService.saveNewOmrAnswers(studentId, testId, new SaveAnswersRequest(request.answers(), request.expectedQuestions())));
    }
}
