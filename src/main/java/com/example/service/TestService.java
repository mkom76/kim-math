package com.example.service;

import com.example.dto.*;
import com.example.entity.*;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
    
    public Page<TestDto> getTests(Pageable pageable) {
        return testRepository.findAll(pageable).map(TestDto::from);
    }
    
    public TestDto getTest(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        return TestDto.from(test);
    }
    
    public TestDto createTest(TestDto dto) {
        Academy academy = academyRepository.findById(dto.getAcademyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

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

        test.setTitle(dto.getTitle());

        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(test.getAcademy().getId())) {
            Academy academy = academyRepository.findById(dto.getAcademyId())
                    .orElseThrow(() -> new RuntimeException("Academy not found"));
            test.setAcademy(academy);
        }

        if (dto.getClassId() != null && !dto.getClassId().equals(test.getAcademyClass().getId())) {
            AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            test.setAcademyClass(academyClass);
        }

        test = testRepository.save(test);
        return TestDto.from(test);
    }
    
    public void deleteTest(Long id) {
        testRepository.deleteById(id);
    }
    
    public void saveTestAnswers(TestAnswersDto dto) {
        Test test = testRepository.findById(dto.getTestId())
                .orElseThrow(() -> new RuntimeException("Test not found"));
        
        // 기존 문제 삭제
        testQuestionRepository.deleteByTestId(dto.getTestId());
        
        // 새로운 문제와 답안 저장
        List<TestQuestion> questions = new ArrayList<>();
        for (TestAnswersDto.QuestionAnswer answer : dto.getAnswers()) {
            TestQuestion question = TestQuestion.builder()
                    .test(test)
                    .number(answer.getNumber())
                    .answer(answer.getAnswer())
                    .build();
            questions.add(question);
        }
        testQuestionRepository.saveAll(questions);
        
        // 기존 제출 답안 재채점
        recalculateScores(dto.getTestId());
    }
    
    private void recalculateScores(Long testId) {
        List<StudentSubmission> submissions = studentSubmissionRepository.findByTestId(testId);
        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByNumber(testId);
        
        for (StudentSubmission submission : submissions) {
            int correctCount = 0;
            
            for (StudentSubmissionDetail detail : submission.getDetails()) {
                TestQuestion question = questions.stream()
                        .filter(q -> q.getNumber().equals(detail.getQuestion().getNumber()))
                        .findFirst()
                        .orElse(null);
                
                if (question != null && question.getAnswer() != null) {
                    boolean isCorrect = question.getAnswer().equals(detail.getStudentAnswer());
                    detail.setIsCorrect(isCorrect);
                    if (isCorrect) correctCount++;
                }
            }
            
            // 총점 계산 (100점 만점 기준)
            int totalScore = questions.isEmpty() ? 0 : (correctCount * 100 / questions.size());
            submission.setTotalScore(totalScore);
        }
        
        studentSubmissionRepository.saveAll(submissions);
    }
    
    public TestStatsDto getTestStats(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));

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

        // 문제별 정답률 및 틀린 학생 명단
        List<Object[]> correctRates = studentSubmissionDetailRepository.getQuestionCorrectRatesByTestId(testId);
        List<TestStatsDto.QuestionStat> questionStats = correctRates.stream()
                .map(arr -> {
                    Integer questionNumber = (Integer) arr[0];
                    Double correctRate = (Double) arr[1];

                    // 해당 문제를 틀린 학생들 찾기
                    List<String> incorrectStudents = submissions.stream()
                            .flatMap(submission -> submission.getDetails().stream()
                                    .filter(detail -> detail.getQuestion().getNumber().equals(questionNumber)
                                            && Boolean.FALSE.equals(detail.getIsCorrect()))
                                    .map(detail -> submission.getStudent().getName()))
                            .collect(Collectors.toList());

                    return TestStatsDto.QuestionStat.builder()
                            .questionNumber(questionNumber)
                            .correctRate(correctRate)
                            .incorrectStudents(incorrectStudents)
                            .build();
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
        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByNumber(testId);
        return questions.stream()
                .map(TestQuestionDto::from)
                .collect(Collectors.toList());
    }

    public TestQuestionDto addQuestion(Long testId, TestQuestionDto dto) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));

        TestQuestion question = TestQuestion.builder()
                .test(test)
                .number(dto.getNumber())
                .answer(dto.getAnswer())
                .points(dto.getPoints())
                .build();

        question = testQuestionRepository.save(question);
        return TestQuestionDto.from(question);
    }

    public void deleteQuestion(Long questionId) {
        testQuestionRepository.deleteById(questionId);
    }

    public List<TestDto> getUnattachedTests(Long academyId, Long classId) {
        return testRepository.findUnattachedByAcademyIdAndClassId(academyId, classId)
                .stream()
                .map(TestDto::from)
                .collect(Collectors.toList());
    }
}