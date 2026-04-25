package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TextbookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager em;

    private Teacher teacher;
    private Academy academy;

    @BeforeEach
    void setUp() {
        academy = new Academy();
        academy.setName("학원A");
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("a"); teacher.setPin("000001");
        em.persist(teacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER).build());
        em.flush();
    }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }

    private Long extractId(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        return node.get("id").asLong();
    }

    @Test
    void create_then_list_then_get_then_update_then_delete() throws Exception {
        String createRes = mockMvc.perform(post("/api/textbooks")
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"title\":\"쎈 수학 상\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("쎈 수학 상"))
                .andExpect(jsonPath("$.ownerTeacherId").value(teacher.getId()))
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createRes);

        mockMvc.perform(get("/api/textbooks").session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("쎈 수학 상"));

        mockMvc.perform(get("/api/textbooks/{id}", id).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("쎈 수학 상"));

        mockMvc.perform(put("/api/textbooks/{id}", id)
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"title\":\"쎈 수학 하\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("쎈 수학 하"));

        mockMvc.perform(delete("/api/textbooks/{id}", id).session(teacherSession()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/textbooks/{id}", id).session(teacherSession()))
                .andExpect(status().isForbidden()); // not-found bubbles to ForbiddenException → 403
    }

    @Test
    void problem_crud_via_endpoints() throws Exception {
        String tbRes = mockMvc.perform(post("/api/textbooks")
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"title\":\"쎈\"}"))
                .andReturn().getResponse().getContentAsString();
        Long tbId = extractId(tbRes);

        String pRes = mockMvc.perform(post("/api/textbooks/{tbId}/problems", tbId)
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"number\":15,\"answer\":\"3\",\"questionType\":\"OBJECTIVE\",\"topic\":\"일차함수\",\"videoLink\":\"https://y/x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("일차함수"))
                .andReturn().getResponse().getContentAsString();
        Long pId = extractId(pRes);

        mockMvc.perform(get("/api/textbooks/{tbId}/problems", tbId).session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(15));

        mockMvc.perform(put("/api/textbook-problems/{id}", pId)
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"number\":15,\"answer\":\"5\",\"questionType\":\"SUBJECTIVE\",\"topic\":\"이차함수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("이차함수"));

        mockMvc.perform(delete("/api/textbook-problems/{id}", pId).session(teacherSession()))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_is_rejected() throws Exception {
        mockMvc.perform(get("/api/textbooks"))
                .andExpect(status().is4xxClientError());
    }
}
