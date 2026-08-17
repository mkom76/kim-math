package com.example.controller;

import com.example.dto.StudentUiFeedbackRequest;
import com.example.dto.StudentUiFeedbackResponse;
import com.example.exception.ForbiddenException;
import com.example.service.StudentUiFeedbackService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student-ui-feedback")
@RequiredArgsConstructor
public class StudentUiFeedbackController {
    private final StudentUiFeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<StudentUiFeedbackResponse> create(
            @Valid @RequestBody StudentUiFeedbackRequest request, HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.create(requireStudentId(session), request));
    }

    private Long requireStudentId(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");
        if (userId == null || !"STUDENT".equals(userRole)) {
            throw new ForbiddenException("학생 로그인이 필요합니다");
        }
        return userId;
    }
}
