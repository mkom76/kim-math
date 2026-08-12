package com.example.service;

import com.example.dto.DailyFeedbackDto;
import com.example.entity.AcademyClass;
import com.example.entity.Lesson;
import com.example.entity.QuestionType;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.StudentSubmissionDetail;
import com.example.entity.TestQuestion;
import com.example.repository.LessonRepository;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentLessonRepository;
import com.example.repository.StudentRepository;
import com.example.repository.StudentSubmissionDetailRepository;
import com.example.repository.StudentSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackServiceTest {

    @Mock private StudentLessonRepository studentLessonRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private StudentHomeworkRepository studentHomeworkRepository;
    @Mock private StudentSubmissionRepository studentSubmissionRepository;
    @Mock private StudentSubmissionDetailRepository studentSubmissionDetailRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private DailyFeedbackService service;

    @Test
    void copiesTestQuestionTopicsIntoAllQuestionResults() {
        Student student = Student.builder().id(10L).name("김학생").build();
        com.example.entity.Test test = com.example.entity.Test.builder()
                .id(20L).title("중간고사").build();
        Lesson lesson = Lesson.builder()
                .id(30L).lessonDate(LocalDate.of(2026, 8, 12))
                .academyClass(AcademyClass.builder().id(40L).build())
                .test(test).build();
        StudentSubmission submission = StudentSubmission.builder()
                .id(50L).student(student).test(test).totalScore(70).build();

        TestQuestion correctQuestion = question(test, 1, QuestionType.OBJECTIVE, "함수 › 일차함수", 10.0);
        TestQuestion wrongQuestion = question(test, 2, QuestionType.SUBJECTIVE, "도형 › 닮음", 10.0);
        TestQuestion essayQuestion = question(test, 3, QuestionType.ESSAY, "확률과 통계", 10.0);
        List<StudentSubmissionDetail> details = List.of(
                detail(submission, correctQuestion, true, 10.0),
                detail(submission, wrongQuestion, false, 0.0),
                detail(submission, essayQuestion, null, 6.0));

        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson));
        when(studentLessonRepository.findByStudentIdAndLessonId(10L, 30L)).thenReturn(Optional.empty());
        when(lessonRepository.findNextLessonsAfter(40L, lesson.getLessonDate())).thenReturn(List.of());
        when(studentSubmissionRepository.findByStudentIdAndTestId(10L, 20L)).thenReturn(Optional.of(submission));
        when(studentSubmissionDetailRepository.findBySubmissionId(50L)).thenReturn(details);
        when(studentSubmissionDetailRepository.getQuestionCorrectRatesByTestId(20L)).thenReturn(List.of(
                new Object[]{1, 100.0, QuestionType.OBJECTIVE},
                new Object[]{2, 0.0, QuestionType.SUBJECTIVE},
                new Object[]{3, 60.0, QuestionType.ESSAY}));
        when(studentSubmissionRepository.findByTestId(20L)).thenReturn(List.of(submission));

        DailyFeedbackDto.TestFeedback result = service.getDailyFeedback(10L, 30L).getTodayTest();

        assertThat(result.getQuestionAccuracyRates())
                .extracting(DailyFeedbackDto.QuestionAccuracy::getQuestionNumber,
                        DailyFeedbackDto.QuestionAccuracy::getTopic)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "함수 › 일차함수"),
                        org.assertj.core.groups.Tuple.tuple(2, "도형 › 닮음"));
        assertThat(result.getIncorrectQuestions()).containsExactly(2);
        assertThat(result.getEssayDetails()).singleElement().satisfies(essay -> {
            assertThat(essay.getQuestionNumber()).isEqualTo(3);
            assertThat(essay.getTopic()).isEqualTo("확률과 통계");
            assertThat(essay.getEarnedPoints()).isEqualTo(6.0);
        });
    }

    private static TestQuestion question(com.example.entity.Test test, int number,
                                         QuestionType type, String topic, double points) {
        return TestQuestion.builder()
                .test(test).number(number).questionType(type).topic(topic).points(points).build();
    }

    private static StudentSubmissionDetail detail(StudentSubmission submission, TestQuestion question,
                                                  Boolean correct, Double earnedPoints) {
        return StudentSubmissionDetail.builder()
                .submission(submission).question(question).isCorrect(correct).earnedPoints(earnedPoints).build();
    }
}
