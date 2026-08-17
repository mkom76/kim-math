package com.example.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.student-ui.v2-default-academy-ids=2,4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentUiPolicyAuthTest {

    @Autowired MockMvc mockMvc;

    @Test
    void current_student_response_exposes_v2_default_for_rollout_academy() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(studentSession(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentUiDefaultMode").value("v2"));
    }

    @Test
    void current_student_response_keeps_legacy_default_for_other_academies() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(studentSession(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentUiDefaultMode").value("legacy"));
    }

    private MockHttpSession studentSession(Long academyId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 100L);
        session.setAttribute("userName", "테스트 학생");
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academyId);
        return session;
    }
}
