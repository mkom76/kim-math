package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.AcademyRepository;
import com.example.repository.TeacherAcademyRepository;
import com.example.repository.TeacherRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.csrf.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AcademyRepository academyRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired TeacherAcademyRepository teacherAcademyRepository;

    @Test
    void rename_requires_csrf_token_with_authenticated_admin_session() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("이름 변경 학원").build());
        Teacher teacher = teacherRepository.save(Teacher.builder().name("기존 이름")
                .username("rename-csrf").build());
        teacherAcademyRepository.save(TeacherAcademy.builder().teacherId(teacher.getId())
                .academyId(academy.getId()).role(TeacherAcademyRole.ACADEMY_ADMIN).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "ACADEMY_ADMIN");
        mockMvc.perform(patch("/api/admin/teachers/{id}/name", teacher.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(teacherRepository.findById(teacher.getId()).orElseThrow().getName())
                .isEqualTo("기존 이름");
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        JsonNode body = objectMapper.readTree(csrf.getResponse().getContentAsString());
        mockMvc.perform(patch("/api/admin/teachers/{id}/name", teacher.getId()).session(session)
                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header(body.get("headerName").asText(), body.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("새 이름"));
    }

    @Test
    void csrf_endpoint_returns_token_and_cookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void csrf_response_token_allows_teacher_login() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("CSRF 학원").build());
        Teacher teacher = new Teacher();
        teacher.setName("CSRF 선생님");
        teacher.setUsername("csrf-teacher");
        teacher.setPin("123456");
        teacher = teacherRepository.save(teacher);
        teacherAcademyRepository.save(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.ACADEMY_ADMIN)
                .build());

        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfBody = objectMapper.readTree(csrf.getResponse().getContentAsString());
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/teacher/login")
                        .cookie(csrfCookie)
                        .header(csrfBody.get("headerName").asText(), csrfBody.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"csrf-teacher","pin":"123456"}
                                """))
                .andExpect(status().isOk());
    }
}
