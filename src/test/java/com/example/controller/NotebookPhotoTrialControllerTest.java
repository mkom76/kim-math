package com.example.controller;

import com.example.service.notebook.*;
import com.example.service.notebook.NotebookTypes.Solution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.solution-notebook.trial.enabled=true","app.solution-notebook.trial.allowed-account=STUDENT:1:1:1","server.address=127.0.0.1","app.security.csrf.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"local","test"})
class NotebookPhotoTrialControllerTest {
    static final Path marker=Path.of(System.getProperty("java.io.tmpdir"),"qa-controller-"+UUID.randomUUID()+".used");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("app.solution-notebook.trial.marker-file",marker::toString);}
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @MockitoBean NotebookPhotoAi ai;
    @MockitoBean com.example.service.ai.OpenAiApiClient feedback;
    @AfterAll static void cleanup() throws Exception {Files.deleteIfExists(marker);}
    @Test void realEndpointRequiresConsentAndOwnerAndReturnsOnlyVerifiedPixels() throws Exception {
        when(ai.ready()).thenReturn(true);
        var draft=new NotebookPhotoAi.Draft("1+1?",new Solution("더하기",List.of("1+1=2"),"2",""));
        var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=out.toByteArray();
        when(ai.solve(any())).thenReturn(draft);when(ai.verifyMathReport(any(),any())).thenReturn(NotebookVerification.legacy(NotebookVerification.Stage.MATH,true));when(ai.render(any())).thenReturn(png);when(ai.verifyImageReport(any(),any())).thenReturn(NotebookVerification.legacy(NotebookVerification.Stage.IMAGE,true));
        var s=session(1L);
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(s).with(csrf()).contentType("image/png").content(png)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(s).with(csrf()).header("X-Notebook-Consent","sample-v1").contentType("image/png").content(new byte[4*1024*1024+1])).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(session(2L)).with(csrf()).header("X-Notebook-Consent","sample-v1").contentType("image/png").content(png)).andExpect(status().isServiceUnavailable());
        verify(ai,never()).solve(any());
        var response=mvc.perform(post("/api/solution-notebook/photo-jobs").session(s).with(csrf()).header("X-Notebook-Consent","sample-v1").contentType("image/png").content(png)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String id=mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
        boolean done=false;for(int i=0;i<100;i++){var state=mvc.perform(get("/api/solution-notebook/photo-jobs/"+id).session(s)).andReturn();if(mapper.readTree(state.getResponse().getContentAsString()).path("state").asText().equals("SUCCEEDED")){done=true;break;}Thread.sleep(10);}assertThat(done).isTrue();
        mvc.perform(get("/api/solution-notebook/photo-jobs/"+id+"/image").session(session(1L))).andExpect(status().isNotFound());
        mvc.perform(get("/api/solution-notebook/photo-jobs/"+id+"/image").session(s)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(png));
        mvc.perform(delete("/api/solution-notebook/photo-jobs/"+id).session(s).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/solution-notebook/photo-jobs/"+id).session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/solution-notebook/trial-config").session(s)).andExpect(jsonPath("$.used").value(true));
        mvc.perform(post("/api/solution-notebook/photo-jobs").session(s).with(csrf()).header("X-Notebook-Consent","sample-v1").contentType("image/png").content(png)).andExpect(status().isTooManyRequests());
        verify(ai,times(1)).solve(any());verify(ai,times(1)).render(any());verifyNoInteractions(feedback);
    }
    static MockHttpSession session(Long id){var s=new MockHttpSession();s.setAttribute("userId",id);s.setAttribute("userRole","STUDENT");s.setAttribute("studentAcademyId",1L);s.setAttribute("activeStudentClassId",1L);return s;}
}
