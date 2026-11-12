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

@SpringBootTest(properties={"app.solution-notebook.live.enabled=true","server.address=127.0.0.1","app.security.csrf.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"local","test"})
class HandwrittenSolutionControllerTest {
    static final Path marker=Path.of(System.getProperty("java.io.tmpdir"),"qa-live-controller-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("app.solution-notebook.live.usage-dir",marker::toString);}
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @MockitoBean NotebookPhotoAi ai;
    @MockitoBean com.example.service.ai.OpenAiApiClient feedback;
    @AfterAll static void cleanup() throws Exception {if(Files.exists(marker)) {try(var paths=Files.walk(marker)){for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);}}}
    @Test void realEndpointRequiresConsentAndOwnerAndReturnsOnlyVerifiedPixels() throws Exception {
        when(ai.ready()).thenReturn(true);
        var draft=new NotebookPhotoAi.Draft("1+1?",new Solution("더하기",List.of("1+1=2"),"2",""));
        var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=out.toByteArray();
        when(ai.solve(any())).thenReturn(draft);when(ai.verifyMath(any(),any())).thenReturn(true);when(ai.render(any())).thenReturn(png);when(ai.verifyImage(any(),any())).thenReturn(true);
        var s=session(1L);
        mvc.perform(post("/api/solution-notebook/live-jobs").session(s).with(csrf()).contentType("image/png").content(png)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/solution-notebook/live-jobs").session(s).with(csrf()).header("X-Notebook-Consent","notebook-v1").contentType("image/png").content(new byte[4*1024*1024+1])).andExpect(status().isPayloadTooLarge());
        mvc.perform(get("/api/solution-notebook/live-config")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/solution-notebook/live-jobs").session(s).header("X-Notebook-Consent","notebook-v1").contentType("image/png").content(png)).andExpect(status().isForbidden());
        mvc.perform(post("/api/solution-notebook/live-jobs").session(s).with(csrf()).header("X-Notebook-Consent","notebook-v1").contentType("application/json").content("{}")).andExpect(status().isUnsupportedMediaType());
        verify(ai,never()).solve(any());
        var response=mvc.perform(post("/api/solution-notebook/live-jobs").with(r->{r.setRemoteAddr("203.0.113.4");return r;}).session(s).with(csrf()).header("X-Notebook-Consent","notebook-v1").contentType("image/png").content(png)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String id=mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
        boolean done=false;for(int i=0;i<100;i++){var state=mvc.perform(get("/api/solution-notebook/live-jobs/"+id).session(s)).andReturn();if(mapper.readTree(state.getResponse().getContentAsString()).path("state").asText().equals("SUCCEEDED")){done=true;break;}Thread.sleep(10);}assertThat(done).isTrue();
        mvc.perform(get("/api/solution-notebook/live-jobs/"+id+"/image").session(session(1L))).andExpect(status().isNotFound());
        mvc.perform(get("/api/solution-notebook/live-jobs/"+id+"/image").session(s)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(png));
        mvc.perform(delete("/api/solution-notebook/live-jobs/"+id).session(s).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/solution-notebook/live-jobs/"+id).session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/solution-notebook/live-config").session(s)).andExpect(jsonPath("$.dailyLimit").value(3)).andExpect(jsonPath("$.remainingToday").value(2)).andExpect(jsonPath("$.enabled").value(true));
        verify(ai,times(1)).solve(any());verify(ai,times(1)).render(any());verifyNoInteractions(feedback);
    }
    static MockHttpSession session(Long id){var s=new MockHttpSession();s.setAttribute("userId",id);s.setAttribute("userRole","STUDENT");s.setAttribute("studentAcademyId",1L);s.setAttribute("activeStudentClassId",1L);return s;}
}
