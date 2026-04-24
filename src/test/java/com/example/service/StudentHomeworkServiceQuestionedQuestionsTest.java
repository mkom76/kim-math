package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentHomeworkDto;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.entity.StudentHomework;
import com.example.exception.ForbiddenException;
import com.example.repository.StudentHomeworkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentHomeworkServiceQuestionedQuestionsTest {

    @Autowired
    private StudentHomeworkService studentHomeworkService;

    @Autowired
    private StudentHomeworkRepository studentHomeworkRepository;

    @PersistenceContext
    private EntityManager em;

    private Student student;
    private Homework homework;

    @BeforeEach
    void setUp() {
        Academy academy = Academy.builder().name("학원A").build();
        em.persist(academy);
        AcademyClass academyClass = AcademyClass.builder().name("반1").academy(academy).build();
        em.persist(academyClass);
        student = Student.builder()
                .name("학생1").grade("중3").school("학교A")
                .academy(academy).academyClass(academyClass).build();
        em.persist(student);
        homework = Homework.builder()
                .title("숙제1").questionCount(20)
                .academy(academy).academyClass(academyClass).build();
        em.persist(homework);
        em.flush();

        TenantContext.setStudent(student.getId(), academy.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void creates_student_homework_with_normalized_questioned_questions() {
        StudentHomeworkDto dto = studentHomeworkService.updateQuestionedQuestions(
                student.getId(), homework.getId(), "3, 7, 7, 12");

        assertThat(dto.getQuestionedQuestions()).isEqualTo("3,7,12");

        StudentHomework saved = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                .orElseThrow();
        assertThat(saved.getQuestionedQuestions()).isEqualTo("3,7,12");
    }

    @Test
    void updates_existing_student_homework() {
        studentHomeworkService.updateQuestionedQuestions(student.getId(), homework.getId(), "1");
        studentHomeworkService.updateQuestionedQuestions(student.getId(), homework.getId(), "2,3");

        StudentHomework saved = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                .orElseThrow();
        assertThat(saved.getQuestionedQuestions()).isEqualTo("2,3");
    }

    @Test
    void allows_clearing_with_null_or_empty() {
        studentHomeworkService.updateQuestionedQuestions(student.getId(), homework.getId(), "1,2");
        studentHomeworkService.updateQuestionedQuestions(student.getId(), homework.getId(), "");

        StudentHomework saved = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                .orElseThrow();
        assertThat(saved.getQuestionedQuestions()).isNull();
    }

    @Test
    void rejects_other_student_attempting_to_modify() {
        // Switch context to a different student
        TenantContext.setStudent(student.getId() + 999, student.getAcademy().getId());

        assertThatThrownBy(() -> studentHomeworkService.updateQuestionedQuestions(
                student.getId(), homework.getId(), "1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejects_teacher_or_unauthenticated_context() {
        TenantContext.clear();

        assertThatThrownBy(() -> studentHomeworkService.updateQuestionedQuestions(
                student.getId(), homework.getId(), "1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejects_out_of_range_question_number() {
        assertThatThrownBy(() -> studentHomeworkService.updateQuestionedQuestions(
                student.getId(), homework.getId(), "25"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
