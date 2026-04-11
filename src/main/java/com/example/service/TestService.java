package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.*;
import com.example.entity.*;
import com.example.exception.ForbiddenException;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TestService {
    private final TestRepository testRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final StudentSubmissionRepository studentSubmissionRepository;
    private final StudentSubmissionDetailRepository studentSubmissionDetailRepository;
    private final StudentRepository studentRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final LessonService lessonService;
    private final AuthorizationService authorizationService;
    
    public Page<TestDto> getTests(Pageable pageable) {
        return testRepository.findAll(pageable).map(TestDto::from);
    }
    
    public TestDto getTest(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);
        return TestDto.from(test);
    }

    public TestDto createTest(TestDto dto) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(ctx.academyId())) {
            throw new ForbiddenException("활성 학원과 다른 학원에 시험을 생성할 수 없습니다");
        }

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));
        authorizationService.assertCanModifyClass(academyClass);

        Test test = Test.builder()
                .title(dto.getTitle())
                .academy(academy)
                .academyClass(academyClass)
                .lesson(null)  // Lesson will be attached later via LessonService
                .build();

        test = testRepository.save(test);
        return TestDto.from(test);
    }

    public TestDto updateTest(Long id, TestDto dto) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        test.setTitle(dto.getTitle());

        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(test.getAcademy().getId())) {
            Academy academy = academyRepository.findById(dto.getAcademyId())
                    .orElseThrow(() -> new RuntimeException("Academy not found"));
            test.setAcademy(academy);
        }

        if (dto.getClassId() != null && !dto.getClassId().equals(test.getAcademyClass().getId())) {
            AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            authorizationService.assertCanModifyClass(academyClass);
            test.setAcademyClass(academyClass);
        }

        test = testRepository.save(test);
        return TestDto.from(test);
    }

    public void deleteTest(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);
        testRepository.delete(test);
    }

    public void saveTestAnswers(TestAnswersDto dto) {
        Test test = testRepository.findById(dto.getTestId())
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        // 기존 문제 조회
        List<TestQuestion> existingQuestions = testQuestionRepository.findByTestIdOrderByNumber(dto.getTestId());

        // 기존 문제를 Map으로 변환 (문제 번호 -> TestQuestion)
        java.util.Map<Integer, TestQuestion> existingQuestionsMap = existingQuestions.stream()
                .collect(java.util.stream.Collectors.toMap(TestQuestion::getNumber, q -> q));

        // 새로운 문제 번호 Set
        java.util.Set<Integer> newQuestionNumbers = dto.getAnswers().stream()
                .map(TestAnswersDto.QuestionAnswer::getNumber)
                .collect(java.util.stream.Collectors.toSet());

        // 삭제할 문제 (새로운 문제 리스트에 없는 문제들)
        List<TestQuestion> questionsToDelete = existingQuestions.stream()
                .filter(q -> !newQuestionNumbers.contains(q.getNumber()))
                .collect(java.util.stream.Collectors.toList());

        // 업데이트 또는 생성
        List<TestQuestion> questionsToSave = new ArrayList<>();
        for (TestAnswersDto.QuestionAnswer answer : dto.getAnswers()) {
            TestQuestion question = existingQuestionsMap.get(answer.getNumber());
            Double points = answer.getPoints() != null ? answer.getPoints() : 0.0;
            QuestionType questionType = answer.getQuestionType() != null ? answer.getQuestionType() : QuestionType.SUBJECTIVE;

            if (question != null) {
                // 기존 문제 업데이트 (ID 유지 - 학생 답안 보존)
                question.setAnswer(answer.getAnswer());
                question.setPoints(points);
                question.setQuestionType(questionType);
            } else {
                // 새 문제 생성
                question = TestQuestion.builder()
                        .test(test)
                        .number(answer.getNumber())
                        .answer(answer.getAnswer())
                        .points(points)
                        .questionType(questionType)
                        .build();
            }
            questionsToSave.add(question);
        }

        // 저장
        testQuestionRepository.saveAll(questionsToSave);

        // 삭제 (CASCADE로 관련 StudentSubmissionDetail도 삭제됨 - 학생이 제출하지 않은 새로 삭제된 문제만)
        if (!questionsToDelete.isEmpty()) {
            testQuestionRepository.deleteAll(questionsToDelete);
        }

        // 기존 제출 답안 재채점
        recalculateScores(dto.getTestId());
    }
    
    public void recalculateScores(Long testId) {
        Test testForAuth = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(testForAuth);

        List<StudentSubmission> submissions = studentSubmissionRepository.findByTestId(testId);
        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByNumber(testId);

        for (StudentSubmission submission : submissions) {
            double earnedPoints = 0.0;
            double totalPoints = 0.0;

            for (StudentSubmissionDetail detail : submission.getDetails()) {
                TestQuestion question = questions.stream()
                        .filter(q -> q.getNumber().equals(detail.getQuestion().getNumber()))
                        .findFirst()
                        .orElse(null);

                if (question != null) {
                    // 배점 합산
                    totalPoints += question.getPoints();

                    if (question.getAnswer() != null) {
                        boolean isCorrect = question.getAnswer().equals(detail.getStudentAnswer());
                        detail.setIsCorrect(isCorrect);
                        if (isCorrect) {
                            earnedPoints += question.getPoints();
                        }
                    }
                }
            }

            // 총점 계산 (배점 기반, 반올림) - submitAnswers와 동일한 방식
            int totalScore = totalPoints == 0 ? 0 : (int) Math.round((earnedPoints / totalPoints) * 100);
            submission.setTotalScore(totalScore);
        }

        studentSubmissionRepository.saveAll(submissions);
    }

    public TestStatsDto getTestStats(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        // 평균 점수
        Double averageScore = studentSubmissionRepository.getAverageScoreByTestId(testId);
        if (averageScore == null) averageScore = 0.0;

        // 학생별 점수
        List<StudentSubmission> submissions = studentSubmissionRepository.findByTestId(testId);
        List<TestStatsDto.StudentScore> studentScores = submissions.stream()
                .map(s -> TestStatsDto.StudentScore.builder()
                        .studentId(s.getStudent().getId())
                        .studentName(s.getStudent().getName())
                        .totalScore(s.getTotalScore())
                        .build())
                .sorted((a, b) -> b.getTotalScore().compareTo(a.getTotalScore())) // 점수 내림차순 정렬
                .collect(Collectors.toList());

        // 최고 점수
        Integer maxScore = submissions.stream()
                .map(StudentSubmission::getTotalScore)
                .max(Integer::compareTo)
                .orElse(0);

        // ESSAY 평균 획득률 맵 (문제번호 → 평균 획득률%)
        Map<Integer, Double> essayAvgEarnedMap = new HashMap<>();
        studentSubmissionDetailRepository.getEssayAvgEarnedRatesByTestId(testId)
                .forEach(arr -> essayAvgEarnedMap.put((Integer) arr[0], (Double) arr[1]));

        // 문제별 정답률 및 틀린/미채점 학생 명단
        List<Object[]> correctRates = studentSubmissionDetailRepository.getQuestionCorrectRatesByTestId(testId);
        List<TestStatsDto.QuestionStat> questionStats = correctRates.stream()
                .map(arr -> {
                    Integer questionNumber = (Integer) arr[0];
                    Double correctRate = (Double) arr[1];
                    QuestionType questionType = (QuestionType) arr[2];

                    if (questionType == QuestionType.ESSAY) {
                        // 서술형: 미채점 학생 목록
                        List<String> pendingStudents = submissions.stream()
                                .flatMap(submission -> submission.getDetails().stream()
                                        .filter(detail -> detail.getQuestion().getNumber().equals(questionNumber)
                                                && detail.getEarnedPoints() == null)
                                        .map(detail -> submission.getStudent().getName()))
                                .collect(Collectors.toList());

                        return TestStatsDto.QuestionStat.builder()
                                .questionNumber(questionNumber)
                                .questionType(questionType.name())
                                .avgEarnedRate(essayAvgEarnedMap.get(questionNumber)) // null = 아직 아무도 채점 안 됨
                                .incorrectStudents(pendingStudents)
                                .build();
                    } else {
                        // 객관식/주관식: 틀린 학생 목록
                        List<String> incorrectStudents = submissions.stream()
                                .flatMap(submission -> submission.getDetails().stream()
                                        .filter(detail -> detail.getQuestion().getNumber().equals(questionNumber)
                                                && Boolean.FALSE.equals(detail.getIsCorrect()))
                                        .map(detail -> submission.getStudent().getName()))
                                .collect(Collectors.toList());

                        return TestStatsDto.QuestionStat.builder()
                                .questionNumber(questionNumber)
                                .questionType(questionType.name())
                                .correctRate(correctRate)
                                .incorrectStudents(incorrectStudents)
                                .build();
                    }
                })
                .collect(Collectors.toList());

        return TestStatsDto.builder()
                .testId(test.getId())
                .testTitle(test.getTitle())
                .averageScore(averageScore)
                .maxScore(maxScore)
                .studentScores(studentScores)
                .questionStats(questionStats)
                .build();
    }
    
    public List<TestQuestionDto> getTestQuestions(Long testId) {
        Test testForAuth = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(testForAuth);

        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByNumber(testId);
        return questions.stream()
                .map(TestQuestionDto::from)
                .collect(Collectors.toList());
    }

    public TestQuestionDto addQuestion(Long testId, TestQuestionDto dto) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        QuestionType questionType = dto.getQuestionType() != null ? dto.getQuestionType() : QuestionType.SUBJECTIVE;

        TestQuestion question = TestQuestion.builder()
                .test(test)
                .number(dto.getNumber())
                .answer(dto.getAnswer())
                .points(dto.getPoints())
                .questionType(questionType)
                .build();

        question = testQuestionRepository.save(question);
        return TestQuestionDto.from(question);
    }

    public void deleteQuestion(Long questionId) {
        TestQuestion question = testQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        authorizationService.assertCanAccessTest(question.getTest());
        testQuestionRepository.delete(question);
    }

    public List<TestDto> getUnattachedTests(Long academyId, Long classId) {
        return testRepository.findUnattachedByAcademyIdAndClassId(academyId, classId)
                .stream()
                .map(TestDto::from)
                .collect(Collectors.toList());
    }
}