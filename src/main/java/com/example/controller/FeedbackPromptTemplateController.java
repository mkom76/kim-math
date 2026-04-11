package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.dto.FeedbackPromptTemplateDto;
import com.example.entity.TeacherAcademyRole;
import com.example.exception.ForbiddenException;
import com.example.service.FeedbackPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback-prompt-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class FeedbackPromptTemplateController {

    private final FeedbackPromptTemplateService service;

    private void assertPathMatchesCaller(Long pathTeacherId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.teacherId() == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 인증이 필요합니다");
        }
        if (ctx.role() == TeacherAcademyRole.ACADEMY_ADMIN) {
            return;
        }
        if (!ctx.teacherId().equals(pathTeacherId)) {
            throw new ForbiddenException("본인의 프롬프트 템플릿만 접근할 수 있습니다");
        }
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<FeedbackPromptTemplateDto> getByTeacher(@PathVariable Long teacherId) {
        assertPathMatchesCaller(teacherId);
        return ResponseEntity.ok(service.getByTeacherId(teacherId));
    }

    @PutMapping("/teacher/{teacherId}")
    public ResponseEntity<FeedbackPromptTemplateDto> save(
            @PathVariable Long teacherId,
            @RequestBody FeedbackPromptTemplateDto dto) {
        assertPathMatchesCaller(teacherId);
        dto.setTeacherId(teacherId);
        return ResponseEntity.ok(service.save(dto));
    }
}
