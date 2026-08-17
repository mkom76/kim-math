package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentUiFeedback;
import com.example.repository.StudentUiFeedbackRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentUiFeedbackControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private StudentUiFeedbackRepository feedbackRepository;
    @PersistenceContext private EntityManager entityManager;

    private Student student;

    @BeforeEach
    void setUp() {
        Academy academy = Academy.builder().name("피드백 학원").build();
        entityManager.persist(academy);
        AcademyClass academyClass = AcademyClass.builder()
                .name("피드백 반")
                .academy(academy)
                .build();
        entityManager.persist(academyClass);
        student = Student.builder()
                .name("학생")
                .grade("고1")
                .school("테스트고")
                .academy(academy)
                .academyClass(academyClass)
                .build();
        entityManager.persist(student);
        entityManager.flush();
    }

    @Test
    void student_can_submit_feedback_and_identity_comes_from_session() throws Exception {
        mockMvc.perform(post("/api/student-ui-feedback")
                        .session(studentSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sentiment": "IMPROVEMENT",
                                  "category": "NAVIGATION",
                                  "message": "  시험 메뉴를 더 빨리 찾고 싶어요.  ",
                                  "pagePath": "/student/dashboard",
                                  "uiVersion": "v2",
                                  "viewportWidth": 360,
                                  "platform": "web",
                                  "appVersion": "1.0.3"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        StudentUiFeedback saved = feedbackRepository.findAll().getFirst();
        assertThat(saved.getStudentId()).isEqualTo(student.getId());
        assertThat(saved.getAcademyId()).isEqualTo(student.getAcademy().getId());
        assertThat(saved.getMessage()).isEqualTo("시험 메뉴를 더 빨리 찾고 싶어요.");
        assertThat(saved.getPagePath()).isEqualTo("/student/dashboard");
    }

    @Test
    void positive_feedback_can_be_submitted_without_category_or_message() throws Exception {
        mockMvc.perform(post("/api/student-ui-feedback")
                        .session(studentSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sentiment": "POSITIVE",
                                  "pagePath": "/settings",
                                  "uiVersion": "v2"
                                }
                                """))
                .andExpect(status().isCreated());

        StudentUiFeedback saved = feedbackRepository.findAll().getFirst();
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getMessage()).isNull();
    }

    @Test
    void improvement_feedback_requires_a_known_category() throws Exception {
        mockMvc.perform(post("/api/student-ui-feedback")
                        .session(studentSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sentiment": "IMPROVEMENT",
                                  "pagePath": "/student/dashboard",
                                  "uiVersion": "v2"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("불편했던 부분을 선택해주세요"));

        assertThat(feedbackRepository.count()).isZero();
    }

    @Test
    void feedback_rejects_non_v2_page_metadata() throws Exception {
        mockMvc.perform(post("/api/student-ui-feedback")
                        .session(studentSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sentiment": "POSITIVE",
                                  "pagePath": "/admin/students",
                                  "uiVersion": "legacy"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacher_cannot_submit_student_feedback() throws Exception {
        MockHttpSession teacherSession = new MockHttpSession();
        teacherSession.setAttribute("userId", 999L);
        teacherSession.setAttribute("userRole", "TEACHER");

        mockMvc.perform(post("/api/student-ui-feedback")
                        .session(teacherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sentiment": "POSITIVE",
                                  "pagePath": "/student/dashboard",
                                  "uiVersion": "v2"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession studentSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        return session;
    }
}
