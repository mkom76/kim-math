package com.example.service.ai;

import com.example.dto.DailyFeedbackDto;
import com.example.dto.AiFeedbackRequest;
import com.example.config.security.TenantContext;
import com.example.entity.FeedbackPromptTemplate;
import com.example.entity.Lesson;
import com.example.entity.Student;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.FeedbackPromptTemplateRepository;
import com.example.repository.LessonRepository;
import com.example.repository.StudentLessonRepository;
import com.example.repository.StudentRepository;
import com.example.service.AuthorizationService;
import com.example.service.DailyFeedbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFeedbackServiceTest {

    @Mock private DailyFeedbackService dailyFeedbackService;
    @Mock private FeedbackPromptTemplateRepository promptTemplateRepository;
    @Mock private StudentLessonRepository studentLessonRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private OpenAiApiClient openAiApiClient;
    @Mock private BulkAiFeedbackProcessor bulkProcessor;
    @Mock private AuthorizationService authorizationService;
    @Mock private AiScoreHistoryService scoreHistoryService;
    @InjectMocks private AiFeedbackService service;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void includesTopicAndResultForEveryQuestionInAiInput() {
        DailyFeedbackDto.TestFeedback test = DailyFeedbackDto.TestFeedback.builder()
                .testTitle("중간고사")
                .studentScore(80)
                .classAverage(70.0)
                .rank(3)
                .incorrectQuestions(List.of(2))
                .questionAccuracyRates(List.of(
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(1).correctRate(90.0).topic("함수 › 일차함수").build(),
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(2).correctRate(40.0).topic("도형 › 닮음").build(),
                        DailyFeedbackDto.QuestionAccuracy.builder()
                                .questionNumber(3).correctRate(60.0).topic(null).build()))
                .essayDetails(List.of(
                        DailyFeedbackDto.EssayDetail.builder()
                                .questionNumber(4).topic("확률과 통계").maxPoints(10.0)
                                .earnedPoints(6.0).teacherComment("풀이 과정 보완").build()))
                .build();
        DailyFeedbackDto feedback = DailyFeedbackDto.builder().todayTest(test).build();
        Student student = Student.builder().name("김학생").build();

        String input = service.buildCurrentStudentData(feedback, student);

        assertThat(input)
                .contains("1번: 정답 / 유형: 함수 › 일차함수 / 반 정답률: 90%")
                .contains("2번: 오답 / 유형: 도형 › 닮음 / 반 정답률: 40%")
                .contains("3번: 정답 / 유형: 유형 미지정 / 반 정답률: 60%")
                .contains("4번 / 유형: 확률과 통계: 6.0/10.0점 (코멘트: 풀이 과정 보완)");
    }

    @ParameterizedTest
    @CsvSource({"true, 7", "false, 7", "false, 0", "true, 0"})
    void usesSavedHistoryCountIndependentlyOfCustomPrompt(boolean active, int count) {
        FeedbackPromptTemplate template = FeedbackPromptTemplate.builder()
                .systemPrompt("선생님 커스텀 가이드").isActive(active).scoreHistoryCount(count).build();
        when(promptTemplateRepository.findByTeacherId(1L)).thenReturn(Optional.of(template));

        generateAndCheckPrompt(count, active ? template.getSystemPrompt() : DefaultFeedbackPrompt.SYSTEM_PROMPT);
    }

    @Test
    void defaultsToThreePreviousTestsWithoutSavedSettings() {
        when(promptTemplateRepository.findByTeacherId(1L)).thenReturn(Optional.empty());
        generateAndCheckPrompt(3, DefaultFeedbackPrompt.SYSTEM_PROMPT);
    }

    @Test
    void defaultsToThreeForLegacyNullSetting() {
        when(promptTemplateRepository.findByTeacherId(1L)).thenReturn(Optional.of(
                FeedbackPromptTemplate.builder().isActive(false).scoreHistoryCount(null).build()));
        generateAndCheckPrompt(3, DefaultFeedbackPrompt.SYSTEM_PROMPT);
    }

    private void generateAndCheckPrompt(int expectedCount, String expectedSystemPrompt) {
        TenantContext.set(1L, 2L, TeacherAcademyRole.TEACHER);
        Student student = Student.builder().id(10L).name("김학생").build();
        Lesson lesson = Lesson.builder().id(20L).build();
        DailyFeedbackDto feedback = DailyFeedbackDto.builder().todayTest(DailyFeedbackDto.TestFeedback.builder()
                .testTitle("이번 시험").studentScore(82).classAverage(74.0).rank(2).build()).build();
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(lessonRepository.findById(20L)).thenReturn(Optional.of(lesson));
        when(dailyFeedbackService.getDailyFeedback(10L, 20L)).thenReturn(feedback);
        String history = expectedCount == 0 ? "" : "\n## 성적 추이\n- 직전 반영 시험 대비 점수 증감: +4점\n";
        when(scoreHistoryService.buildSummary(10L, lesson, feedback.getTodayTest(), expectedCount)).thenReturn(history);
        when(openAiApiClient.sendMessage(anyString(), anyList(), isNull())).thenReturn("생성된 피드백");
        AiFeedbackRequest request = new AiFeedbackRequest();
        request.setStudentId(10L);
        request.setLessonId(20L);

        assertThat(service.generateFeedback(request).getGeneratedFeedback()).isEqualTo("생성된 피드백");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> messages = ArgumentCaptor.forClass(List.class);
        verify(openAiApiClient).sendMessage(system.capture(), messages.capture(), isNull());
        assertThat(system.getValue()).startsWith(expectedSystemPrompt)
                .contains(DefaultFeedbackPrompt.SCORE_HISTORY_GUIDELINES);
        assertThat(messages.getValue()).singleElement().satisfies(message -> {
            assertThat(message.get("content")).contains("- 점수: 82점");
            if (expectedCount == 0) {
                assertThat(message.get("content")).doesNotContain("## 성적 추이");
            } else {
                assertThat(message.get("content")).contains(history);
            }
        });
    }
}
