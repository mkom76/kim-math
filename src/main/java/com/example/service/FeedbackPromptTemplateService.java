package com.example.service;

import com.example.dto.FeedbackPromptTemplateDto;
import com.example.entity.FeedbackPromptTemplate;
import com.example.entity.Teacher;
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

    public FeedbackPromptTemplateDto getByTeacherId(Long teacherId) {
        return repository.findByTeacherId(teacherId)
                .map(this::toDto)
                .orElseGet(() -> FeedbackPromptTemplateDto.builder()
                        .teacherId(teacherId)
                        .systemPrompt(DefaultFeedbackPrompt.SYSTEM_PROMPT)
                        .fewShotCount(DefaultFeedbackPrompt.DEFAULT_FEW_SHOT_COUNT)
                        .isActive(true)
                        .build());
    }

    @Transactional
    public FeedbackPromptTemplateDto save(FeedbackPromptTemplateDto dto) {
        Teacher teacher = teacherRepository.findById(dto.getTeacherId())
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        FeedbackPromptTemplate template = repository.findByTeacherId(dto.getTeacherId())
                .orElseGet(() -> FeedbackPromptTemplate.builder()
                        .teacher(teacher)
                        .build());

        template.setSystemPrompt(dto.getSystemPrompt());
        template.setFewShotCount(dto.getFewShotCount());
        template.setIsActive(dto.getIsActive());

        return toDto(repository.save(template));
    }

    private FeedbackPromptTemplateDto toDto(FeedbackPromptTemplate entity) {
        return FeedbackPromptTemplateDto.builder()
                .id(entity.getId())
                .teacherId(entity.getTeacher().getId())
                .systemPrompt(entity.getSystemPrompt())
                .fewShotCount(entity.getFewShotCount())
                .isActive(entity.getIsActive())
                .build();
    }
}
