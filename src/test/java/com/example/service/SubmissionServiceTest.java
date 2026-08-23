package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentSubmissionDto;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.TestQuestion;
import com.example.repository.StudentRepository;
import com.example.repository.StudentSubmissionDetailRepository;
import com.example.repository.StudentSubmissionRepository;
import com.example.repository.TestQuestionRepository;
import com.example.repository.TestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private StudentSubmissionRepository submissionRepository;
    @Mock
    private StudentSubmissionDetailRepository detailRepository;
    @Mock
    private TestQuestionRepository questionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TestRepository testRepository;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private SubmissionService submissionService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void missing_answer_is_saved_as_null_and_graded_incorrect() {
        AcademyClass academyClass = AcademyClass.builder().id(10L).name("A반").build();
        Student student = Student.builder()
                .id(1L)
                .name("학생")
                .grade("중1")
                .school("학교")
                .academyClass(academyClass)
                .build();
        com.example.entity.Test test = com.example.entity.Test.builder()
                .id(2L)
                .title("시험")
                .academyClass(academyClass)
                .build();
        TestQuestion question = TestQuestion.builder()
                .id(3L)
                .test(test)
                .number(1)
                .answer("3")
                .points(10.0)
                .questionType(QuestionType.OBJECTIVE)
                .build();

        TenantContext.setStudent(student.getId(), 20L);
        when(submissionRepository.findByStudentIdAndTestId(student.getId(), test.getId()))
                .thenReturn(Optional.empty());
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(questionRepository.findByTestIdOrderByNumber(test.getId())).thenReturn(List.of(question));
        when(submissionRepository.save(any(StudentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentSubmissionDto result = submissionService.submitMyAnswers(test.getId(), Map.of());

        assertThat(result.getTotalScore()).isZero();
        assertThat(result.getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getStudentAnswer()).isNull();
            assertThat(detail.getIsCorrect()).isFalse();
            assertThat(detail.getEarnedPoints()).isZero();
        });
    }
}
