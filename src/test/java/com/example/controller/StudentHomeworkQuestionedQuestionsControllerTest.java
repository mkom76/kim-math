package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.repository.StudentHomeworkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentHomeworkQuestionedQuestionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentHomeworkRepository studentHomeworkRepository;

    @PersistenceContext
    private EntityManager em;

    private Student student;
    private Homework homework;
    private Academy academy;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
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
    }

    private MockHttpSession studentSession(Long studentId, Long academyId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", studentId);
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academyId);
        return session;
    }

    @Test
    void put_updates_questioned_questions_and_returns_dto() throws Exception {
        mockMvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/questioned-questions",
                        student.getId(), homework.getId())
                        .session(studentSession(student.getId(), academy.getId()))
                        .contentType("application/json")
                        .content("{\"questionedQuestions\":\"7, 3, 12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionedQuestions").value("3,7,12"));

        assertThat(studentHomeworkRepository
                .findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                .orElseThrow()
                .getQuestionedQuestions()).isEqualTo("3,7,12");
    }

    @Test
    void put_with_null_clears_questioned_questions() throws Exception {
        // First set something
        mockMvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/questioned-questions",
                        student.getId(), homework.getId())
                        .session(studentSession(student.getId(), academy.getId()))
                        .contentType("application/json")
                        .content("{\"questionedQuestions\":\"1,2\"}"))
                .andExpect(status().isOk());

        // Then clear via empty string
        mockMvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/questioned-questions",
                        student.getId(), homework.getId())
                        .session(studentSession(student.getId(), academy.getId()))
                        .contentType("application/json")
                        .content("{\"questionedQuestions\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionedQuestions").doesNotExist());
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/questioned-questions",
                        student.getId(), homework.getId())
                        .contentType("application/json")
                        .content("{\"questionedQuestions\":\"1\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void other_student_session_is_rejected() throws Exception {
        // path studentId is `student.getId()`, but session userId is a different student
        mockMvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/questioned-questions",
                        student.getId(), homework.getId())
                        .session(studentSession(student.getId() + 999, academy.getId()))
                        .contentType("application/json")
                        .content("{\"questionedQuestions\":\"1\"}"))
                .andExpect(status().isForbidden());
    }
}
