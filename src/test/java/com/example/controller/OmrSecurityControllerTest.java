package com.example.controller;

import com.example.service.SubmissionService;
import com.example.service.omr.OmrService;
import com.example.service.omr.OmrTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.csrf.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OmrSecurityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OmrService service;
    @MockitoBean SubmissionService submissions;

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        session.setAttribute("userId", 1L); session.setAttribute("userRole", role);
        session.setAttribute("activeAcademyId", 1L); session.setAttribute("activeRole", role);
        session.setAttribute("studentAcademyId", 1L);
        return session;
    }

    @Test void upload_and_confirmation_require_csrf_and_teacher_role() throws Exception {
        when(service.scan(anyLong(), any())).thenReturn(new OmrTypes.Scan(List.of(), List.of(), List.of()));
        mvc.perform(multipart("/api/tests/1/omr/scan").file("file", "%PDF-test".getBytes()).session(session("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tests/1/omr/submissions/2").contentType("application/json")
                        .content("{\"reviewed\":true}").session(session("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tests/1/omr/submissions/2").contentType("application/json")
                        .content("{\"reviewed\":true,\"expectedRevision\":\"new\"}").session(session("TEACHER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, submissions);
        mvc.perform(multipart("/api/tests/1/omr/scan").file("file", "%PDF-test".getBytes()).session(session("TEACHER")).with(csrf()))
                .andExpect(status().isOk());
        for (String role : List.of("STUDENT", "ASSISTANT")) {
            mvc.perform(get("/api/tests/1/omr/submissions/2/review").session(session(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/tests/1/omr/submissions/2").contentType("application/json")
                            .content("{\"reviewed\":true,\"expectedRevision\":\"new\"}").session(session(role)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/tests/1/omr/scan").file("file", "%PDF-test".getBytes()).session(session(role)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/tests/1/omr/submissions/2").contentType("application/json")
                            .content("{\"reviewed\":true}").session(session(role)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verify(service, times(1)).scan(anyLong(), any());
        verifyNoInteractions(submissions);
    }
}
