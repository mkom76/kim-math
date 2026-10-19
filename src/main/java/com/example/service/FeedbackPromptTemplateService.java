package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.FeedbackPromptTemplateDto;
import com.example.entity.FeedbackPromptTemplate;
import com.example.entity.Teacher;
import com.example.exception.ForbiddenException;
import com.example.repository.FeedbackPromptTemplateRepository;
import com.example.repository.TeacherRepository;
import com.example.service.ai.DefaultFeedbackPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackPromptTemplateService {

    private final FeedbackPromptTemplateRepository repository;
    private final TeacherRepository teacherRepository;

    private Long currentTeacherId() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.teacherId() == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 인증이 필요합니다");
        }
        return ctx.teacherId();
    }

    public FeedbackPromptTemplateDto getByTeacherId(Long ignoredTeacherId) {
        Long teacherId = currentTeacherId();
        return repository.findByTeacherId(teacherId)
                .map(this::toDto)
                .orElseGet(() -> FeedbackPromptTemplateDto.builder()
                        .teacherId(teacherId)
                        .systemPrompt(DefaultFeedbackPrompt.SYSTEM_PROMPT)
                        .fewShotCount(DefaultFeedbackPrompt.DEFAULT_FEW_SHOT_COUNT)
                        .scoreHistoryCount(DefaultFeedbackPrompt.DEFAULT_SCORE_HISTORY_COUNT)
                        .isActive(true)
                        .build());
    }

    public FeedbackPromptTemplateDto getDefault() {
        return FeedbackPromptTemplateDto.builder()
                .teacherId(currentTeacherId())
                .systemPrompt(DefaultFeedbackPrompt.SYSTEM_PROMPT)
                .fewShotCount(DefaultFeedbackPrompt.DEFAULT_FEW_SHOT_COUNT)
                .scoreHistoryCount(DefaultFeedbackPrompt.DEFAULT_SCORE_HISTORY_COUNT)
                .isActive(true)
                .build();
    }

    @Transactional
    public FeedbackPromptTemplateDto save(FeedbackPromptTemplateDto dto) {
        Long teacherId = currentTeacherId();
        if (dto.getScoreHistoryCount() != null
                && (dto.getScoreHistoryCount() < 0 || dto.getScoreHistoryCount() > 10)) {
            throw new IllegalArgumentException("이전 시험 반영 개수는 0~10 사이여야 합니다");
        }
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        FeedbackPromptTemplate template = repository.findByTeacherId(teacherId)
                .orElseGet(() -> FeedbackPromptTemplate.builder()
                        .teacher(teacher)
                        .build());

        template.setSystemPrompt(dto.getSystemPrompt());
        template.setFewShotCount(dto.getFewShotCount());
        // Older clients omit this setting; preserve the saved value in that case.
        if (dto.getScoreHistoryCount() != null) {
            template.setScoreHistoryCount(dto.getScoreHistoryCount());
        }
        template.setIsActive(dto.getIsActive());

        return toDto(repository.save(template));
    }

    private FeedbackPromptTemplateDto toDto(FeedbackPromptTemplate entity) {
        return FeedbackPromptTemplateDto.builder()
                .id(entity.getId())
                .teacherId(entity.getTeacher().getId())
                .systemPrompt(entity.getSystemPrompt())
                .fewShotCount(entity.getFewShotCount())
                .scoreHistoryCount(entity.getScoreHistoryCount() != null
                        ? entity.getScoreHistoryCount() : DefaultFeedbackPrompt.DEFAULT_SCORE_HISTORY_COUNT)
                .isActive(entity.getIsActive())
                .build();
    }
}
