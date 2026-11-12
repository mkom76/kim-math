package com.example.controller;

import com.example.service.ai.OpenAiApiClient;
import com.example.service.notebook.NotebookTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.solution-notebook.enabled=true","app.solution-notebook.mock-step-delay-ms=0","app.security.csrf.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolutionNotebookControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean OpenAiApiClient externalAi;
    MockHttpSession student(long id,long academy,long classId) {
        var s=new MockHttpSession();s.setAttribute("userId",id);s.setAttribute("userRole","STUDENT");s.setAttribute("studentAcademyId",academy);s.setAttribute("activeStudentClassId",classId);return s;
    }
    @Test void authenticationAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/solution-notebook/config")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/solution-notebook/read-jobs").session(student(1,1,1)).contentType("application/json").content("{\"fixture\":\"rational-asymptote\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(student(1,1,1)).contentType("image/png").content(new byte[]{1,2})).andExpect(status().isForbidden());
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(student(1,1,1)).with(csrf()).contentType("image/png").content(new byte[]{1,2})).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/solution-notebook/trial-config").session(student(1,1,1))).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
        mvc.perform(get("/api/solution-notebook/trial-config").session(student(1,1,1)).with(r -> {r.setRemoteAddr("203.0.113.1");return r;})).andExpect(status().isForbidden());
        verifyNoInteractions(externalAi);
    }
    @Test void mockContractRejectsPhotosOversizedBodiesAndMalformedRequests() throws Exception {
        var s=student(2,1,1);
        mvc.perform(post("/api/solution-notebook/read-jobs").session(s).with(csrf()).contentType("application/json").content("{\"fixture\":\"rational-asymptote\",\"image\":\"private-photo\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/solution-notebook/read-jobs").session(s).with(csrf()).contentType("application/json").content(" ".repeat(12001))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/solution-notebook/read-jobs").session(s).with(csrf()).contentType("text/plain").content("abc")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/solution-notebook/read-jobs").session(s).with(csrf()).contentType("application/json").content("{")).andExpect(status().isBadRequest());
        verifyNoInteractions(externalAi);
    }
    @Test void jobAccessIsBoundToTheAuthenticatedAccountAcademyClassAndSession() throws Exception {
        var session=student(10,1,1);
        var create=mvc.perform(post("/api/solution-notebook/read-jobs").session(session).with(csrf()).contentType("application/json").content("{\"fixture\":\"rational-asymptote\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String id=mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/solution-notebook/jobs/"+id).session(student(11,1,1))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/solution-notebook/jobs/"+id).session(student(10,2,1)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/solution-notebook/jobs/"+id+"/image").session(student(10,1,2))).andExpect(status().isNotFound());
        session.setAttribute("studentAcademyId",2L);
        mvc.perform(get("/api/solution-notebook/jobs/"+id).session(session)).andExpect(status().isNotFound());
        session.setAttribute("studentAcademyId",1L);
        mvc.perform(delete("/api/solution-notebook/jobs/"+id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/solution-notebook/jobs/"+id).session(session)).andExpect(status().isNotFound());
        verifyNoInteractions(externalAi);
    }
}
