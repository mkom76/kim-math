package com.example.service;

import com.example.dto.DailyFeedbackDto;
import com.example.dto.StudentLessonDto;
import com.example.entity.*;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyFeedbackService {
    private final StudentLessonRepository studentLessonRepository;
    private final LessonRepository lessonRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final StudentSubmissionRepository studentSubmissionRepository;
    private final StudentSubmissionDetailRepository studentSubmissionDetailRepository;
    private final StudentRepository studentRepository;

    public DailyFeedbackDto getTodayFeedback(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        LocalDate today = LocalDate.now();

        Optional<Lesson> todayLesson = lessonRepository.findByAcademyIdAndClassIdAndLessonDate(
            student.getAcademy().getId(), student.getAcademyClass().getId(), today);

        if (todayLesson.isEmpty()) {
            throw new RuntimeException("No lesson scheduled for today");
        }

        return getDailyFeedback(studentId, todayLesson.get().getId());
    }

    public DailyFeedbackDto getDailyFeedback(Long studentId, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        StudentLesson studentLesson = studentLessonRepository
                .findByStudentIdAndLessonId(studentId, lessonId)
                .orElse(null);

        DailyFeedbackDto feedback = new DailyFeedbackDto();
        feedback.setLessonId(lessonId);
        feedback.setLessonDate(lesson.getLessonDate());

        // A. Today's homework - 학생에게 할당된 숙제만 조회
        if (!lesson.getHomeworks().isEmpty()) {
            Optional<Homework> assignedHomework = getAssignedHomework(studentId, lesson.getHomeworks());
            assignedHomework.ifPresent(homework ->
                feedback.setTodayHomework(getHomeworkSummary(studentId, homework)));
        }

        // Next homework - 다음 수업에서 학생에게 할당된 숙제만 조회
        List<Lesson> nextLessons = lessonRepository.findNextLessonsAfter(
            lesson.getAcademyClass().getId(), lesson.getLessonDate());
        if (!nextLessons.isEmpty() && !nextLessons.get(0).getHomeworks().isEmpty()) {
            Optional<Homework> nextAssignedHomework = getAssignedHomework(studentId, nextLessons.get(0).getHomeworks());
            nextAssignedHomework.ifPresent(homework ->
                feedback.setNextHomework(getHomeworkSummary(studentId, homework)));
        }

        // B. Today's test with incorrect questions and academy accuracy
        if (lesson.getTest() != null) {
            feedback.setTodayTest(getTestFeedback(studentId, lesson.getTest()));
        }

        // C. Instructor feedback
        if (studentLesson != null) {
            feedback.setInstructorFeedback(studentLesson.getInstructorFeedback());
            feedback.setFeedbackAuthor(studentLesson.getFeedbackAuthor());
        }

        return feedback;
    }

    @Transactional
    public StudentLessonDto updateInstructorFeedback(Long studentId, Long lessonId,
                                                      String feedback, String authorName) {
        return updateInstructorFeedback(studentId, lessonId, feedback, authorName, false);
    }

    @Transactional
    public StudentLessonDto updateInstructorFeedback(Long studentId, Long lessonId,
                                                      String feedback, String authorName,
                                                      boolean isAiFeedback) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        StudentLesson studentLesson = studentLessonRepository
                .findByStudentIdAndLessonId(studentId, lessonId)
                .orElseGet(() -> StudentLesson.builder()
                        .student(student)
                        .lesson(lesson)
                        .build());

        studentLesson.setInstructorFeedback(feedback);
        studentLesson.setFeedbackAuthor(authorName);
        studentLesson.setIsAiFeedback(isAiFeedback);
        return StudentLessonDto.from(studentLessonRepository.save(studentLesson));
    }

    private DailyFeedbackDto.HomeworkSummary getHomeworkSummary(Long studentId, Homework homework) {
        Optional<StudentHomework> sh = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(studentId, homework.getId());

        return DailyFeedbackDto.HomeworkSummary.builder()
                .homeworkId(homework.getId())
                .homeworkTitle(homework.getTitle())
                .questionCount(homework.getQuestionCount())
                .incorrectCount(sh.map(StudentHomework::getIncorrectCount).orElse(null))
                .unsolvedCount(sh.map(StudentHomework::getUnsolvedCount).orElse(null))
                .incorrectQuestions(sh.map(StudentHomework::getIncorrectQuestions).orElse(null))
                .unsolvedQuestions(sh.map(StudentHomework::getUnsolvedQuestions).orElse(null))
                .completion(sh.map(StudentHomework::getCompletion).orElse(null))
                .dueDate(homework.getDueDate())
                .memo(homework.getMemo())
                .build();
    }

    private DailyFeedbackDto.TestFeedback getTestFeedback(Long studentId, Test test) {
        Optional<StudentSubmission> submission = studentSubmissionRepository
                .findByStudentIdAndTestId(studentId, test.getId());

        if (submission.isEmpty()) return null;

        StudentSubmission sub = submission.get();

        List<StudentSubmissionDetail> details = studentSubmissionDetailRepository.findBySubmissionId(sub.getId());

        // Get incorrect question numbers (객관식/주관식만 - ESSAY 제외)
        List<Integer> incorrectQuestions = details.stream()
                .filter(d -> Boolean.FALSE.equals(d.getIsCorrect()))
                .map(d -> d.getQuestion().getNumber())
                .sorted()
                .collect(Collectors.toList());

        // Get essay details
        List<DailyFeedbackDto.EssayDetail> essayDetails = details.stream()
                .filter(d -> d.getQuestion().getQuestionType() == QuestionType.ESSAY)
                .sorted(java.util.Comparator.comparing(d -> d.getQuestion().getNumber()))
                .map(d -> DailyFeedbackDto.EssayDetail.builder()
                        .questionNumber(d.getQuestion().getNumber())
                        .maxPoints(d.getQuestion().getPoints())
                        .studentAnswer(d.getStudentAnswer())
                        .earnedPoints(d.getEarnedPoints())
                        .teacherComment(d.getTeacherComment())
                        .build())
                .collect(Collectors.toList());

        // Get academy accuracy rates (서술형 제외 - questionType으로 필터)
        List<Object[]> accuracyData = studentSubmissionDetailRepository
                .getQuestionCorrectRatesByTestId(test.getId());

        List<DailyFeedbackDto.QuestionAccuracy> rates = accuracyData.stream()
                .filter(arr -> arr[2] != QuestionType.ESSAY)
                .map(arr -> DailyFeedbackDto.QuestionAccuracy.builder()
                        .questionNumber((Integer) arr[0])
                        .correctRate((Double) arr[1])
                        .build())
                .collect(Collectors.toList());

        // Calculate class average and rank
        List<StudentSubmission> allSubmissions = studentSubmissionRepository.findByTestId(test.getId());

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
            if (scores.get(i).equals(sub.getTotalScore())) {
                rank = i + 1;
                break;
            }
        }

        return DailyFeedbackDto.TestFeedback.builder()
                .testId(test.getId())
                .testTitle(test.getTitle())
                .studentScore(sub.getTotalScore())
                .classAverage(classAverage)
                .rank(rank)
                .incorrectQuestions(incorrectQuestions)
                .questionAccuracyRates(rates)
                .essayDetails(essayDetails.isEmpty() ? null : essayDetails)
                .build();
    }

    /**
     * 학생에게 할당된 숙제 찾기
     * @param studentId 학생 ID
     * @param homeworks 숙제 목록
     * @return 할당된 숙제 (Optional)
     */
    private Optional<Homework> getAssignedHomework(Long studentId, List<Homework> homeworks) {
        if (homeworks.isEmpty()) {
            return Optional.empty();
        }

        // 숙제 ID 목록 추출
        List<Long> homeworkIds = homeworks.stream()
                .map(Homework::getId)
                .collect(Collectors.toList());

        // 학생에게 할당된 숙제 찾기
        List<StudentHomework> studentHomeworks = studentHomeworkRepository
                .findByHomeworkIdIn(homeworkIds).stream()
                .filter(sh -> sh.getStudent().getId().equals(studentId))
                .collect(Collectors.toList());

        if (studentHomeworks.isEmpty()) {
            return Optional.empty();
        }

        // 첫 번째 할당된 숙제 반환 (학생은 수업당 1개의 숙제만 할당받음)
        return Optional.of(studentHomeworks.get(0).getHomework());
    }
}
