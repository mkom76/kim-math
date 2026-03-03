package com.example.service;

import com.example.dto.EssayGradeRequest;
import com.example.dto.StudentSubmissionDto;
import com.example.dto.SubmissionDetailDto;
import com.example.entity.*;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    
    public StudentSubmissionDto submitAnswers(Long studentId, Long testId, Map<Integer, String> answers) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        
        // 기존 제출 확인
        StudentSubmission submission = submissionRepository.findByStudentIdAndTestId(studentId, testId)
                .orElse(StudentSubmission.builder()
                        .student(student)
                        .test(test)
                        .build());
        
        // 문제 가져오기
        List<TestQuestion> questions = questionRepository.findByTestIdOrderByNumber(testId);
        
        // 답안 저장 및 채점
        List<StudentSubmissionDetail> details = new ArrayList<>();
        double autoEarnedPoints = 0.0;
        double totalPoints = 0.0;

        for (TestQuestion question : questions) {
            String studentAnswer = answers.get(question.getNumber());
            Boolean isCorrect = null;
            Double earnedPoints = null;

            if (question.getQuestionType() != QuestionType.ESSAY) {
                isCorrect = question.getAnswer() != null &&
                            question.getAnswer().equals(studentAnswer);
                earnedPoints = isCorrect ? question.getPoints() : 0.0;
                autoEarnedPoints += earnedPoints;
            }

            StudentSubmissionDetail detail = StudentSubmissionDetail.builder()
                    .submission(submission)
                    .question(question)
                    .studentAnswer(studentAnswer)
                    .isCorrect(isCorrect)
                    .earnedPoints(earnedPoints)
                    .build();

            details.add(detail);
            totalPoints += question.getPoints();
        }

        // 총점 계산 (배점 기반, 반올림)
        int totalScore = totalPoints == 0 ? 0 : (int) Math.round((autoEarnedPoints / totalPoints) * 100);
        submission.setTotalScore(totalScore);
        submission.setSubmittedAt(LocalDateTime.now());

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
    
    public StudentSubmissionDto getSubmission(Long submissionId) {
        StudentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        
        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        List<SubmissionDetailDto> details = submission.getDetails().stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList());
        dto.setDetails(details);
        
        return dto;
    }
    
    public StudentSubmissionDto getSubmissionByStudentAndTest(Long studentId, Long testId) {
        StudentSubmission submission = submissionRepository.findByStudentIdAndTestId(studentId, testId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        
        StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
        List<SubmissionDetailDto> details = submission.getDetails().stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList());
        dto.setDetails(details);
        
        return dto;
    }
    
    public List<StudentSubmissionDto> getStudentSubmissions(Long studentId) {
        List<StudentSubmission> submissions = submissionRepository.findByStudentId(studentId);

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

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<StudentSubmissionDto> getTestSubmissions(Long testId) {
        List<StudentSubmission> submissions = submissionRepository.findByTestId(testId);
        return submissions.stream()
                .map(submission -> {
                    StudentSubmissionDto dto = StudentSubmissionDto.from(submission);
                    dto.setPendingEssayCount(submissionRepository.countPendingEssays(submission.getId()).intValue());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public SubmissionDetailDto gradeEssay(Long detailId, EssayGradeRequest request) {
        StudentSubmissionDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new RuntimeException("Detail not found"));

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
        recalculateTotalScore(submission);

        return SubmissionDetailDto.from(detail);
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