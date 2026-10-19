package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.FeedbackPromptTemplateDto;
import com.example.entity.FeedbackPromptTemplate;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.FeedbackPromptTemplateRepository;
import com.example.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackPromptTemplateServiceTest {
    @Mock private FeedbackPromptTemplateRepository repository;
    @Mock private TeacherRepository teacherRepository;
    @InjectMocks private FeedbackPromptTemplateService service;

    @BeforeEach
    void setContext() {
        TenantContext.set(1L, 2L, TeacherAcademyRole.TEACHER);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void defaultsAndUnsavedSettingsUseThreePreviousExams() {
        assertThat(service.getDefault().getScoreHistoryCount()).isEqualTo(3);
        assertThat(service.getByTeacherId(1L).getScoreHistoryCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 10})
    void savesCountForCallerEvenWithCustomPromptDisabled(int count) {
        Teacher teacher = Teacher.builder().id(1L).build();
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FeedbackPromptTemplateDto dto = FeedbackPromptTemplateDto.builder()
                .teacherId(999L).systemPrompt("가이드").fewShotCount(3).scoreHistoryCount(count).isActive(false).build();

        FeedbackPromptTemplateDto result = service.save(dto);

        assertThat(result.getTeacherId()).isEqualTo(1L);
        assertThat(result.getScoreHistoryCount()).isEqualTo(count);
        assertThat(result.getIsActive()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void rejectsCountsOutsideAllowedRange(int count) {
        assertThatThrownBy(() -> service.save(FeedbackPromptTemplateDto.builder().scoreHistoryCount(count).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0~10");
        verifyNoInteractions(repository, teacherRepository);
    }

    @Test
    void olderClientOmittingCountPreservesSavedSetting() {
        Teacher teacher = Teacher.builder().id(1L).build();
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(repository.findByTeacherId(1L)).thenReturn(Optional.of(
                FeedbackPromptTemplate.builder().teacher(teacher).scoreHistoryCount(8).build()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.save(FeedbackPromptTemplateDto.builder().systemPrompt("가이드").build())
                .getScoreHistoryCount()).isEqualTo(8);
    }
}
