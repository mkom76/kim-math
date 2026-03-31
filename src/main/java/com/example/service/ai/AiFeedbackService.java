package com.example.service.ai;

import com.example.dto.AiFeedbackRequest;
import com.example.dto.AiFeedbackResponse;
import com.example.dto.BulkAiFeedbackRequest;
import com.example.dto.BulkAiFeedbackResponse;
import com.example.dto.DailyFeedbackDto;
import com.example.entity.FeedbackPromptTemplate;
import com.example.entity.Student;
import com.example.entity.StudentLesson;
import com.example.repository.FeedbackPromptTemplateRepository;
import com.example.repository.StudentLessonRepository;
import com.example.repository.StudentRepository;
import com.example.service.DailyFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiFeedbackService {

    private final DailyFeedbackService dailyFeedbackService;
    private final FeedbackPromptTemplateRepository promptTemplateRepository;
    private final StudentLessonRepository studentLessonRepository;
    private final StudentRepository studentRepository;
    private final OpenAiApiClient openAiApiClient;
    private final BulkAiFeedbackProcessor bulkProcessor;

    // lessonId -> 진행 상태
    private final ConcurrentHashMap<Long, BulkAiFeedbackResponse> bulkStatusMap = new ConcurrentHashMap<>();

    public BulkAiFeedbackResponse getBulkStatus(Long lessonId) {
        return bulkStatusMap.get(lessonId);
    }

    public BulkAiFeedbackResponse startBulkFeedback(BulkAiFeedbackRequest request) {
        Long lessonId = request.getLessonId();

        // 이미 진행 중이면 현재 상태 반환
        BulkAiFeedbackResponse existing = bulkStatusMap.get(lessonId);
        if (existing != null && "PROCESSING".equals(existing.getStatus())) {
            return existing;
        }

        List<StudentLesson> studentLessons = studentLessonRepository.findByLessonId(lessonId);

        // 엔티티 → 스냅샷 변환 (비동기 스레드에서 Lazy 프록시 접근 방지)
        List<BulkAiFeedbackProcessor.StudentSnapshot> snapshots = studentLessons.stream()
                .map(sl -> new BulkAiFeedbackProcessor.StudentSnapshot(
                        sl.getStudent().getId(),
                        sl.getStudent().getName(),
                        sl.getAttendanceStatus(),
                        sl.getInstructorFeedback()))
                .toList();

        // 초기 상태 등록
        BulkAiFeedbackResponse initial = BulkAiFeedbackResponse.builder()
                .status("PROCESSING")
                .totalCount(snapshots.size())
                .processedCount(0)
                .successCount(0)
                .failCount(0)
                .skippedCount(0)
                .skippedStudents(new ArrayList<>())
                .build();
        bulkStatusMap.put(lessonId, initial);

        // 별도 빈을 통한 비동기 실행 (프록시 경유)
        bulkProcessor.processAsync(request, snapshots, initial);

        return initial;
    }

    public AiFeedbackResponse generateFeedback(AiFeedbackRequest request) {
        // 1. 학생 시험 데이터 수집
        DailyFeedbackDto feedbackData = dailyFeedbackService.getDailyFeedback(
                request.getStudentId(), request.getLessonId());

        if (feedbackData.getTodayTest() == null && feedbackData.getTodayHomework() == null) {
            throw new RuntimeException("No test or homework data available for this lesson");
        }

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // 2. 선생님별 프롬프트 템플릿 조회 (없으면 기본값)
        String systemPrompt = promptTemplateRepository.findByTeacherId(request.getTeacherId())
                .filter(FeedbackPromptTemplate::getIsActive)
                .map(FeedbackPromptTemplate::getSystemPrompt)
                .orElse(DefaultFeedbackPrompt.SYSTEM_PROMPT);

        int fewShotCount = promptTemplateRepository.findByTeacherId(request.getTeacherId())
                .map(FeedbackPromptTemplate::getFewShotCount)
                .orElse(DefaultFeedbackPrompt.DEFAULT_FEW_SHOT_COUNT);

        // 3. Few-shot 예시 수집 (해당 선생님이 이전에 작성한 피드백)
        List<Map<String, String>> messages = buildMessages(
                feedbackData, student, request.getTeacherId(), fewShotCount);

        // 4. OpenAI API 호출
        String generated = openAiApiClient.sendMessage(systemPrompt, messages, request.getModel());

        return AiFeedbackResponse.builder()
                .generatedFeedback(generated)
                .studentId(request.getStudentId())
                .lessonId(request.getLessonId())
                .build();
    }

    private List<Map<String, String>> buildMessages(
            DailyFeedbackDto feedbackData, Student student,
            Long teacherId, int fewShotCount) {

        List<Map<String, String>> messages = new ArrayList<>();

        // Few-shot 예시: 해당 선생님이 작성한 최근 피드백을 예시로 포함
        List<StudentLesson> recentFeedbacks = studentLessonRepository
                .findRecentByFeedbackAuthorTeacherId(teacherId, fewShotCount);

        for (StudentLesson example : recentFeedbacks) {
            String exampleInput = buildStudentDataSummary(example);
            if (exampleInput != null) {
                messages.add(Map.of("role", "user", "content", exampleInput));
                messages.add(Map.of("role", "assistant", "content", example.getInstructorFeedback()));
            }
        }

        // 실제 요청: 현재 학생의 시험 데이터
        String currentInput = buildCurrentStudentData(feedbackData, student);
        messages.add(Map.of("role", "user", "content", currentInput));

        return messages;
    }

    private String buildCurrentStudentData(DailyFeedbackDto feedbackData, Student student) {
        StringBuilder sb = new StringBuilder();

        // 시험 데이터
        DailyFeedbackDto.TestFeedback test = feedbackData.getTodayTest();
        if (test != null) {
            sb.append("## 학생 시험 결과\n");
            sb.append("- 학생: ").append(student.getName()).append("\n");
            sb.append("- 시험: ").append(test.getTestTitle()).append("\n");
            sb.append("- 점수: ").append(test.getStudentScore()).append("점\n");
            sb.append("- 반 평균: ").append(String.format("%.1f", test.getClassAverage())).append("점\n");
            sb.append("- 순위: ").append(test.getRank()).append("등\n\n");

            if (!test.getIncorrectQuestions().isEmpty()) {
                sb.append("## 오답 문제 (서술형 제외)\n");
                for (Integer qNum : test.getIncorrectQuestions()) {
                    test.getQuestionAccuracyRates().stream()
                            .filter(q -> q.getQuestionNumber().equals(qNum))
                            .findFirst()
                            .ifPresent(q -> sb.append("- ").append(qNum).append("번: 반 정답률 ")
                                    .append(String.format("%.0f", q.getCorrectRate())).append("%\n"));
                }
            } else {
                sb.append("## 객관식/주관식 전문항 정답\n");
            }

            if (test.getEssayDetails() != null && !test.getEssayDetails().isEmpty()) {
                sb.append("\n## 서술형\n");
                for (DailyFeedbackDto.EssayDetail essay : test.getEssayDetails()) {
                    sb.append("- ").append(essay.getQuestionNumber()).append("번: ");
                    sb.append(essay.getEarnedPoints() != null ? essay.getEarnedPoints() : "미채점");
                    sb.append("/").append(essay.getMaxPoints()).append("점");
                    if (essay.getTeacherComment() != null) {
                        sb.append(" (코멘트: ").append(essay.getTeacherComment()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }

        // 숙제 데이터
        DailyFeedbackDto.HomeworkSummary homework = feedbackData.getTodayHomework();
        if (homework != null) {
            sb.append("\n## 숙제 결과\n");
            sb.append("- 숙제: ").append(homework.getHomeworkTitle()).append("\n");
            sb.append("- 전체 문제 수: ").append(homework.getQuestionCount()).append("문항\n");
            if (homework.getCompletion() != null) {
                sb.append("- 완성도: ").append(homework.getCompletion()).append("%\n");
                sb.append("- 오답 개수: ").append(homework.getIncorrectCount() != null ? homework.getIncorrectCount() : 0).append("개\n");
                sb.append("- 안 푼 문제 수: ").append(homework.getUnsolvedCount() != null ? homework.getUnsolvedCount() : 0).append("개\n");
                if (homework.getIncorrectQuestions() != null && !homework.getIncorrectQuestions().isEmpty()) {
                    sb.append("- 오답 문항번호: ").append(homework.getIncorrectQuestions()).append("\n");
                }
                if (homework.getUnsolvedQuestions() != null && !homework.getUnsolvedQuestions().isEmpty()) {
                    sb.append("- 안 푼 문항번호: ").append(homework.getUnsolvedQuestions()).append("\n");
                }
            } else {
                sb.append("- 미제출\n");
            }
            if (homework.getMemo() != null && !homework.getMemo().isEmpty()) {
                sb.append("- 숙제 메모 (문항별 유형 정보): ").append(homework.getMemo()).append("\n");
            }
        }

        sb.append("\n위 데이터를 바탕으로 피드백을 작성해주세요.");
        return sb.toString();
    }

    private String buildStudentDataSummary(StudentLesson studentLesson) {
        try {
            DailyFeedbackDto data = dailyFeedbackService.getDailyFeedback(
                    studentLesson.getStudent().getId(),
                    studentLesson.getLesson().getId());
            if (data.getTodayTest() == null) return null;
            return buildCurrentStudentData(data, studentLesson.getStudent());
        } catch (Exception e) {
            log.warn("Failed to build few-shot example for studentLesson {}", studentLesson.getId(), e);
            return null;
        }
    }
}
