package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.EssayGradeRequest;
import com.example.dto.ErrorCausesDto;
import com.example.dto.StudentSubmissionDto;
import com.example.dto.SubmissionDetailDto;
import com.example.dto.QuestionSnapshot;
import com.example.dto.SaveAnswersRequest;
import com.example.entity.*;
import com.example.exception.ForbiddenException;
import com.example.repository.*;
import com.example.service.omr.OmrRevision;
import com.example.service.omr.OmrService;
import com.example.service.omr.OmrTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SubmissionService {
    private final StudentSubmissionRepository submissionRepository;
    private final StudentSubmissionDetailRepository detailRepository;
    private final TestQuestionRepository questionRepository;
    private final StudentRepository studentRepository;
    private final TestRepository testRepository;
    private final AuthorizationService authorizationService;
    private final StudentClassEnrollmentService studentClassEnrollmentService;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ErrorCausesDto saveErrorCauses(Long submissionId, Map<String, BigDecimal> counts) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 권한이 필요합니다");
        }
        lockTestForSubmissionWrite(submissionRepository.findTestIdForSubmission(submissionId));
        StudentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "응시 기록이 없습니다"));
        authorizationService.assertCanAccessSubmission(submission);
        if (counts == null || !counts.keySet().equals(Set.of(
                "calculationMistake", "conceptGap", "conditionMisread", "ideaGap", "timeManagement"))) {
            throw new IllegalArgumentException("5개 원인을 모두 보내 주세요. 빈 값은 0으로 처리합니다");
        }
        for (BigDecimal count : counts.values()) {
            if (count == null) continue;
            try {
                if (count.intValueExact() < 0) throw new ArithmeticException();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("원인 건수는 0 이상 2147483647 이하의 정수여야 합니다");
            }
        }
        submission.setCalculationMistake(integerCount(counts.get("calculationMistake")));
        submission.setConceptGap(integerCount(counts.get("conceptGap")));
        submission.setConditionMisread(integerCount(counts.get("conditionMisread")));
        submission.setIdeaGap(integerCount(counts.get("ideaGap")));
        submission.setTimeManagement(integerCount(counts.get("timeManagement")));
        submission.setErrorCausesNeedReview(false);
        return ErrorCausesDto.from(submissionRepository.save(submission));
    }

    private Integer integerCount(BigDecimal count) {
        return count == null ? 0 : count.intValueExact();
    }

    public StudentSubmissionDto submitMyAnswers(Long testId, SaveAnswersRequest request) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null) {
            throw new ForbiddenException("학생 인증이 필요합니다");
        }
        return submitAnswersAsStudent(ctx.teacherId(), testId, request);
    }

    public StudentSubmissionDto submitAnswers(Long studentId, Long testId, SaveAnswersRequest request) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (ctx.role() == null) {
            if (!ctx.teacherId().equals(studentId)) {
                throw new ForbiddenException("본인 답안만 제출할 수 있습니다");
            }
            return submitAnswersAsStudent(studentId, testId, request);
        }
        return saveAnswersForStudent(studentId, testId, request);
    }

    private StudentSubmissionDto submitAnswersAsStudent(Long studentId, Long testId, SaveAnswersRequest request) {
        authorizationService.assertStudentClassWritable();
        if (submissionRepository.findByStudentIdAndTestId(studentId, testId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 제출한 시험은 수정할 수 없습니다");
        }
        return saveSubmission(studentId, testId, request, false);
    }

    public StudentSubmissionDto saveAnswersForStudent(Long studentId, Long testId, SaveAnswersRequest request) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null) {
            throw new ForbiddenException("선생님 권한이 필요합니다");
        }
        return saveSubmission(studentId, testId, request, true);
    }

    public StudentSubmissionDto saveNewOmrAnswers(Long studentId, Long testId, SaveAnswersRequest request) {
        // Keep the original POST create-only for older clients. Updates require an
        // explicit review snapshot through the new PUT endpoint.
        return saveOmrAnswers(studentId, testId, new OmrTypes.Update(request.answers(), request.expectedQuestions(), "new", true));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OmrTypes.Review reviewOmrAnswers(Long studentId, Long testId) {
        assertOmrTeacher();
        Test test = testRepository.findByIdForQuestionRead(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시험이 없습니다"));
        authorizationService.assertCanAccessTest(test);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학생이 없습니다"));
        authorizationService.assertCanAccessStudent(student);
        assertStudentBelongsToTest(student, test);
        List<TestQuestion> questions = questionRepository.findByTestIdForSnapshot(testId);
        OmrService.validateQuestions(questions);
        StudentSubmission submission = submissionRepository.findByStudentIdAndTestId(studentId, testId).orElse(null);
        var details = submission == null ? Map.<Long, StudentSubmissionDetail>of() : submission.getDetails().stream()
                .collect(Collectors.toMap(d -> d.getQuestion().getId(), d -> d));
        double preserved = questions.stream().filter(q -> q.getQuestionType() != QuestionType.OBJECTIVE)
                .map(q -> details.get(q.getId())).filter(Objects::nonNull).filter(d -> d.getEarnedPoints() != null)
                .mapToDouble(StudentSubmissionDetail::getEarnedPoints).sum();
        return new OmrTypes.Review(OmrRevision.of(submission), submission != null,
                submission == null ? null : submission.getTotalScore(), preserved,
                questions.stream().mapToDouble(q -> q.getPoints() == null ? 0 : q.getPoints()).sum(),
                (int) questions.stream().filter(q -> q.getQuestionType() != QuestionType.OBJECTIVE).count(),
                questions.stream().filter(q -> q.getQuestionType() == QuestionType.OBJECTIVE).map(q ->
                        new OmrTypes.ObjectiveAnswer(q.getNumber(), details.containsKey(q.getId()) ? details.get(q.getId()).getStudentAnswer() : null,
                                q.getAnswer(), Boolean.TRUE.equals(q.getMultipleAnswers()), q.getPoints() == null ? 0 : q.getPoints())).toList(),
                questions.stream().map(QuestionSnapshot::from).toList());
    }

    private void assertOmrTeacher() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null || ctx.role() == TeacherAcademyRole.ASSISTANT) {
            throw new ForbiddenException("선생님 권한이 필요합니다");
        }
    }

    public StudentSubmissionDto saveOmrAnswers(Long studentId, Long testId, OmrTypes.Update request) {
        assertOmrTeacher();
        Test test = testRepository.findByIdForQuestionWrite(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시험이 없습니다"));
        authorizationService.assertCanAccessTest(test);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학생이 없습니다"));
        authorizationService.assertCanAccessStudent(student);
        assertStudentBelongsToTest(student, test);
        List<TestQuestion> questions = questionRepository.findByTestIdForSnapshot(testId);
        OmrService.validateQuestions(questions);
        if (request == null || !request.reviewed() || !questions.stream().map(QuestionSnapshot::from).toList().equals(request.expectedQuestions())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시험 문항이 변경되었습니다. PDF를 다시 인식한 뒤 확인해 주세요.");
        }
        Set<Integer> numbers = questions.stream().filter(q -> q.getQuestionType() == QuestionType.OBJECTIVE)
                .map(TestQuestion::getNumber).collect(Collectors.toSet());
        if (request.answers() == null || !numbers.equals(request.answers().keySet())
                || request.answers().values().stream().anyMatch(value -> value == null
                || !value.matches("(?:[1-5](?:,[1-5]){0,4})?"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "객관식 문항의 답안만 1~5 또는 무응답으로 입력해 주세요.");
        }
        StudentSubmission submission = submissionRepository.findForAnswerWrite(studentId, testId).orElse(null);
        if (!OmrRevision.of(submission).equals(request.expectedRevision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "답안 또는 채점 결과가 변경되었습니다. 학생을 다시 선택해 기존 성적을 확인해 주세요.");
        }
        boolean existing = submission != null;
        if (!existing) submission = StudentSubmission.builder().student(student).test(test).submittedAt(LocalDateTime.now()).build();
        var details = submission.getDetails().stream().collect(Collectors.toMap(d -> d.getQuestion().getId(), d -> d));
        for (TestQuestion question : questions) {
            StudentSubmissionDetail detail = details.get(question.getId());
            if (detail == null) {
                // Existing non-objective records (including their absence) are untouched.
                if (existing && question.getQuestionType() != QuestionType.OBJECTIVE) continue;
                detail = StudentSubmissionDetail.builder().submission(submission).question(question).build();
                submission.getDetails().add(detail);
                if (question.getQuestionType() == QuestionType.SUBJECTIVE) {
                    detail.setIsCorrect(false); detail.setEarnedPoints(0.0);
                }
            }
            if (question.getQuestionType() == QuestionType.OBJECTIVE) {
                String answer = normalizeStudentAnswer(request.answers().get(question.getNumber()));
                boolean correct = TestAnswerMatcher.matches(question, answer);
                detail.setStudentAnswer(answer); detail.setIsCorrect(correct);
                detail.setEarnedPoints(correct && question.getPoints() != null ? question.getPoints() : 0.0);
            }
        }
        double earned = submission.getDetails().stream().filter(d -> d.getEarnedPoints() != null).mapToDouble(StudentSubmissionDetail::getEarnedPoints).sum();
        double max = questions.stream().mapToDouble(q -> q.getPoints() == null ? 0 : q.getPoints()).sum();
        submission.setTotalScore(max == 0 ? 0 : (int) Math.round(earned / max * 100));
        submission.markErrorCausesForReview();
        submission = submissionRepository.saveAndFlush(submission);
        var dto = StudentSubmissionDto.from(submission);
        dto.setDetails(submission.getDetails().stream().map(SubmissionDetailDto::from).toList());
        dto.setPendingEssayCount((int) submission.getDetails().stream().filter(d -> d.getQuestion().getQuestionType() == QuestionType.ESSAY && d.getEarnedPoints() == null).count());
        return dto;
    }

    private StudentSubmissionDto saveSubmission(Long studentId, Long testId, SaveAnswersRequest request, boolean allowExistingUpdate) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        Test test = testRepository.findByIdForQuestionRead(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);
        assertStudentBelongsToTest(student, test);

        // 문항 변경 트랜잭션과 함께 잠그고, 답안을 변경하기 전에 입력 화면의 문항을 검증한다.
        List<TestQuestion> questions = questionRepository.findByTestIdForSnapshot(testId);
        List<QuestionSnapshot> currentQuestions = questions.stream().map(QuestionSnapshot::from).toList();
        if (request == null || request.answers() == null || !currentQuestions.equals(request.expectedQuestions())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "시험 문항이 변경되었거나 문항 정보를 확인할 수 없습니다. 새로고침 후 다시 시도해주세요");
        }
        Map<Integer, String> answers = request.answers();
        
        // 기존 제출 확인
        StudentSubmission submission = submissionRepository.findForAnswerWrite(studentId, testId)
                .orElse(StudentSubmission.builder()
                        .student(student)
                        .test(test)
                        .build());
        if (submission.getId() != null && !allowExistingUpdate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 제출한 시험은 수정할 수 없습니다");
        }

        Map<Integer, StudentSubmissionDetail> previousDetails = submission.getDetails().stream()
                .collect(Collectors.toMap(
                        detail -> detail.getQuestion().getNumber(),
                        detail -> detail,
                        (left, right) -> left));
        
        // 답안 저장 및 채점
        List<StudentSubmissionDetail> details = new ArrayList<>();
        double earnedPointsTotal = 0.0;
        double totalPoints = 0.0;

        for (TestQuestion question : questions) {
            String studentAnswer = normalizeStudentAnswer(answers.get(question.getNumber()));
            Boolean isCorrect = null;
            Double earnedPoints = null;
            String teacherComment = null;
            QuestionType questionType = question.getQuestionType() != null
                    ? question.getQuestionType()
                    : QuestionType.SUBJECTIVE;
            double maxPoints = question.getPoints() != null ? question.getPoints() : 0.0;

            if (questionType != QuestionType.ESSAY) {
                isCorrect = TestAnswerMatcher.matches(question, studentAnswer);
                earnedPoints = isCorrect ? maxPoints : 0.0;
                earnedPointsTotal += earnedPoints;
            } else {
                StudentSubmissionDetail previous = previousDetails.get(question.getNumber());
                if (previous != null && Objects.equals(previous.getStudentAnswer(), studentAnswer)) {
                    earnedPoints = previous.getEarnedPoints();
                    teacherComment = previous.getTeacherComment();
                    if (earnedPoints != null) {
                        earnedPointsTotal += earnedPoints;
                    }
                }
            }

            StudentSubmissionDetail detail = StudentSubmissionDetail.builder()
                    .submission(submission)
                    .question(question)
                    .studentAnswer(studentAnswer)
                    .isCorrect(isCorrect)
                    .earnedPoints(earnedPoints)
                    .teacherComment(teacherComment)
                    .build();

            details.add(detail);
            totalPoints += maxPoints;
        }

        // 총점 계산 (배점 기반, 반올림)
        int totalScore = totalPoints == 0 ? 0 : (int) Math.round((earnedPointsTotal / totalPoints) * 100);
        submission.setTotalScore(totalScore);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.markErrorCausesForReview();

        // 기존 상세 답안 삭제 후 새로 저장
        submission.getDetails().clear();
        submission.getDetails().addAll(details);
        
        submission = submissionRepository.save(submission);
        
        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        dto.setDetails(details.stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList()));
        
        return dto;
    }

    private String normalizeStudentAnswer(String studentAnswer) {
        return studentAnswer == null || studentAnswer.isBlank() ? null : studentAnswer;
    }

    private void assertStudentBelongsToTest(Student student, Test test) {
        Long testClassId = test.getAcademyClass() != null ? test.getAcademyClass().getId() : null;
        if (testClassId == null
                || !studentClassEnrollmentService.isActivelyEnrolled(student.getId(), testClassId)) {
            throw new ForbiddenException("해당 시험 반의 학생만 답안을 제출할 수 있습니다");
        }
    }
    
    public StudentSubmissionDto getSubmission(Long submissionId) {
        StudentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        authorizationService.assertCanAccessSubmission(submission);

        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        List<SubmissionDetailDto> details = submission.getDetails().stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList());
        dto.setDetails(details);
        
        return dto;
    }
    
    public StudentSubmissionDto getMyResultByTest(Long testId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null) {
            throw new ForbiddenException("학생 인증이 필요합니다");
        }
        Long studentId = ctx.teacherId();
        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentId, testId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "응시 기록이 없습니다"));
        authorizationService.assertCanAccessSubmission(submission);

        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        dto.setDetails(submission.getDetails().stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList()));
        maskScoresIfHiddenForStudent(dto, submission.getStudent());
        return dto;
    }

    /**
     * 학생 본인 호출인 경우, 해당 학생의 hideScoresFromStudent 플래그가 켜져 있으면
     * 점수/등수/반평균을 null로 마스킹한다. 선생님 호출은 영향 없음.
     */
    private void maskScoresIfHiddenForStudent(StudentSubmissionDto dto, Student student) {
        TenantContext.Context ctx = TenantContext.current();
        boolean isStudentCaller = ctx != null && ctx.role() == null;
        if (isStudentCaller && Boolean.TRUE.equals(student.getHideScoresFromStudent())) {
            dto.setTotalScore(null);
            dto.setClassAverage(null);
            dto.setRank(null);
        }
    }

    public StudentSubmissionDto getSubmissionByStudentAndTest(Long studentId, Long testId) {
        StudentSubmission submission = submissionRepository.findByStudentIdAndTestId(studentId, testId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        authorizationService.assertCanAccessSubmission(submission);

        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        List<SubmissionDetailDto> details = submission.getDetails().stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList());
        dto.setDetails(details);
        
        return dto;
    }
    
    public List<StudentSubmissionDto> getStudentSubmissions(Long studentId, Long activeClassId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        List<StudentSubmission> submissions = activeClassId == null
                ? submissionRepository.findByStudentId(studentId)
                : submissionRepository.findByStudentIdAndTestAcademyClassId(studentId, activeClassId);

        return submissions.stream()
                .map(submission -> {
                    StudentSubmissionDto dto = StudentSubmissionDto.from(submission);

                    // Calculate class average and rank for this test
                    List<StudentSubmission> allSubmissions = submissionRepository.findByTestId(submission.getTest().getId());

                    // Class average
                    double classAverage = allSubmissions.stream()
                            .mapToInt(StudentSubmission::getTotalScore)
                            .average()
                            .orElse(0.0);

                    // Calculate rank
                    List<Integer> scores = allSubmissions.stream()
                            .map(StudentSubmission::getTotalScore)
                            .sorted((a, b) -> b.compareTo(a)) // Descending order
                            .collect(Collectors.toList());

                    int rank = 1;
                    for (int i = 0; i < scores.size(); i++) {
                        if (scores.get(i).equals(submission.getTotalScore())) {
                            rank = i + 1;
                            break;
                        }
                    }

                    dto.setClassAverage(classAverage);
                    dto.setRank(rank);
                    dto.setPendingEssayCount(submissionRepository.countPendingEssays(submission.getId()).intValue());
                    maskScoresIfHiddenForStudent(dto, submission.getStudent());

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<StudentSubmissionDto> getTestSubmissions(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        List<StudentSubmission> submissions = submissionRepository.findByTestId(testId);
        return submissions.stream()
                .map(submission -> {
                    StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
                    dto.setPendingEssayCount(submissionRepository.countPendingEssays(submission.getId()).intValue());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // Resolve only a scalar ID before the test lock. READ_COMMITTED ensures a
    // writer that waited for OMR sees the newly committed objective details.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SubmissionDetailDto gradeEssay(Long detailId, EssayGradeRequest request) {
        lockTestForSubmissionWrite(detailRepository.findTestIdForDetail(detailId));
        StudentSubmissionDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new RuntimeException("Detail not found"));
        authorizationService.assertCanAccessSubmission(detail.getSubmission());

        if (detail.getQuestion().getQuestionType() != QuestionType.ESSAY) {
            throw new RuntimeException("Only ESSAY type questions can be manually graded");
        }

        double maxPoints = detail.getQuestion().getPoints();
        double earned = request.getEarnedPoints() == null ? 0.0 : request.getEarnedPoints();
        if (earned < 0 || earned > maxPoints) {
            throw new RuntimeException("earnedPoints must be between 0 and " + maxPoints);
        }

        detail.setEarnedPoints(earned);
        detail.setTeacherComment(request.getTeacherComment());
        detail = detailRepository.save(detail);

        StudentSubmission submission = submissionRepository.findById(detail.getSubmission().getId())
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        submission.markErrorCausesForReview();
        recalculateTotalScore(submission);

        return SubmissionDetailDto.from(detail);
    }

    private void lockTestForSubmissionWrite(Long testId) {
        if (testId == null) throw new ForbiddenException("답안에 접근할 수 없습니다");
        Test test = testRepository.findByIdForQuestionWrite(testId)
                .orElseThrow(() -> new ForbiddenException("시험에 접근할 수 없습니다"));
        authorizationService.assertCanAccessTest(test);
    }

    private void recalculateTotalScore(StudentSubmission submission) {
        double earnedTotal = submission.getDetails().stream()
                .filter(d -> d.getEarnedPoints() != null)
                .mapToDouble(StudentSubmissionDetail::getEarnedPoints)
                .sum();
        double maxTotal = submission.getDetails().stream()
                .mapToDouble(d -> d.getQuestion().getPoints())
                .sum();
        int totalScore = maxTotal == 0 ? 0 : (int) Math.round((earnedTotal / maxTotal) * 100);
        submission.setTotalScore(totalScore);
        submissionRepository.save(submission);
    }
}
