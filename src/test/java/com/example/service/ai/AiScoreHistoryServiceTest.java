package com.example.service.ai;

import com.example.dto.DailyFeedbackDto;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Lesson;
import com.example.entity.StudentSubmission;
import com.example.repository.StudentSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiScoreHistoryServiceTest {
    @Mock private StudentSubmissionRepository repository;
    @InjectMocks private AiScoreHistoryService service;

    private final Lesson lesson = Lesson.builder().lessonDate(LocalDate.of(2026, 10, 20))
            .academy(Academy.builder().id(2L).build())
            .academyClass(AcademyClass.builder().id(3L).build()).build();

    @Test
    void includesAvailableHistoryChronologicallyAndCalculatesBothChanges() {
        when(repository.findRecentGradedBeforeDate(1L, 2L, 3L, lesson.getLessonDate(), PageRequest.of(0, 3)))
                .thenReturn(List.of(previous(20L, 19, 78), previous(10L, 18, 70)));
        when(repository.getAverageScoresByTestIds(List.of(20L, 10L)))
                .thenReturn(List.of(new Object[]{20L, 72.0}, new Object[]{10L, 68.0}));

        String result = service.buildSummary(1L, lesson, current(82), 3);

        assertThat(result).contains("이전 시험 2회 + 이번 시험")
                .containsSubsequence("2026-10-18", "2026-10-19", "2026-10-20 (이번)")
                .contains("점수: 70점 / 반 평균: 68.0점 / 평균 대비: +2.0점")
                .contains("점수: 78점 / 반 평균: 72.0점 / 평균 대비: +6.0점")
                .contains("점수: 82점 / 반 평균: 74.0점 / 평균 대비: +8.0점")
                .contains("직전 반영 시험 대비 점수 증감: +4점")
                .contains("직전 반영 시험 대비 반 평균 격차 증감: +2.0점");
    }

    @ParameterizedTest
    @CsvSource({"70, -8, -10.0", "78, +0, -2.0", "82, +4, +2.0"})
    void calculatesDeclineNoChangeAndImprovement(int currentScore, String change, String gapChange) {
        when(repository.findRecentGradedBeforeDate(1L, 2L, 3L, lesson.getLessonDate(), PageRequest.of(0, 1)))
                .thenReturn(List.of(previous(20L, 19, 78)));
        when(repository.getAverageScoresByTestIds(List.of(20L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{20L, 72.0}));

        assertThat(service.buildSummary(1L, lesson, current(currentScore), 1))
                .contains("점수 증감: " + change + "점")
                .contains("반 평균 격차 증감: " + gapChange + "점");
    }

    @Test
    void zeroDisablesHistoryWithoutQuerying() {
        assertThat(service.buildSummary(1L, lesson, current(82), 0)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void homeworkOnlyDoesNotQueryHistory() {
        assertThat(service.buildSummary(1L, lesson, null, 3)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void pendingCurrentEssayPreventsComparison() {
        DailyFeedbackDto.TestFeedback test = current(82);
        test.setEssayDetails(List.of(DailyFeedbackDto.EssayDetail.builder().earnedPoints(null).build()));

        assertThat(service.buildSummary(1L, lesson, test, 3)).contains("채점이 완료되지 않아 성적 비교에서 제외");
        verifyNoInteractions(repository);
    }

    @Test
    void noPreviousResultsExplicitlyPreventsTrendClaims() {
        assertThat(service.buildSummary(1L, lesson, current(82), 3))
                .contains("이전 시험 기록이 없어 성적 추이를 판단할 수 없습니다");
        verify(repository, never()).getAverageScoresByTestIds(anyList());
    }

    private DailyFeedbackDto.TestFeedback current(int score) {
        return DailyFeedbackDto.TestFeedback.builder().testTitle("이번 시험")
                .studentScore(score).classAverage(74.0).build();
    }

    private StudentSubmission previous(Long testId, int day, int score) {
        var test = com.example.entity.Test.builder().id(testId).title("이전 시험 " + testId)
                .lesson(Lesson.builder().lessonDate(LocalDate.of(2026, 10, day)).build()).build();
        return StudentSubmission.builder().test(test).totalScore(score).build();
    }
}
