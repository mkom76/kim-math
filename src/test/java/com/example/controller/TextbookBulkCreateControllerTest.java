package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Textbook;
import com.example.repository.TextbookProblemRepository;
import com.example.repository.TextbookRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TextbookBulkCreateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TextbookProblemRepository textbookProblemRepository;
    @PersistenceContext private EntityManager em;

    private Teacher teacher;
    private Teacher otherTeacher;
    private Academy academy;
    private Textbook textbook;
    private Textbook otherTextbook;

    @BeforeEach
    void setUp() {
        academy = new Academy();
        academy.setName("학원A");
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("a"); teacher.setPin("000001");
        em.persist(teacher);
        otherTeacher = new Teacher();
        otherTeacher.setName("선생B"); otherTeacher.setUsername("b"); otherTeacher.setPin("000002");
        em.persist(otherTeacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER).build());

        textbook = textbookRepoSave(Textbook.builder()
                .ownerTeacherId(teacher.getId()).title("내 교재").build());
        otherTextbook = textbookRepoSave(Textbook.builder()
                .ownerTeacherId(otherTeacher.getId()).title("타인 교재").build());
        em.flush();
    }

    @Autowired private TextbookRepository textbookRepository;
    private Textbook textbookRepoSave(Textbook tb) { return textbookRepository.save(tb); }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }

    @Test
    void bulk_creates_problems_with_full_and_partial_metadata() throws Exception {
        String body = "[" +
                "{\"number\":1,\"questionType\":\"OBJECTIVE\",\"answer\":\"3\",\"topic\":\"일차함수\",\"videoLink\":\"https://y/1\"}," +
                "{\"number\":2,\"questionType\":\"SUBJECTIVE\",\"topic\":\"이차방정식\"}," +
                "{\"number\":3}" +  // 모두 null/디폴트
                "]";

        mockMvc.perform(post("/api/textbooks/{id}/problems/bulk", textbook.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        var saved = textbookProblemRepository.findByTextbookIdOrderByNumberAsc(textbook.getId());
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getTopic()).isEqualTo("일차함수");
        assertThat(saved.get(2).getQuestionType()).isNull(); // nullable 확인
        assertThat(saved.get(2).getAnswer()).isNull();
    }

    @Test
    void bulk_rejects_non_owner_textbook() throws Exception {
        String body = "[{\"number\":1,\"questionType\":\"OBJECTIVE\"}]";
        mockMvc.perform(post("/api/textbooks/{id}/problems/bulk", otherTextbook.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulk_with_empty_array_returns_empty() throws Exception {
        mockMvc.perform(post("/api/textbooks/{id}/problems/bulk", textbook.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
