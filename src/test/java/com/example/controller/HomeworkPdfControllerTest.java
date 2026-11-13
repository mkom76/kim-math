package com.example.controller;

import com.example.entity.*;
import com.example.repository.*;
import com.example.service.homeworkpdf.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static com.example.service.homeworkpdf.PdfTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.homework-pdf.enabled=true","app.homework-pdf.global-daily-limit=100",
        "spring.datasource.url=jdbc:h2:mem:homework_pdf;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class HomeworkPdfControllerTest {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("kim-math-pdf-test-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.homework-pdf.storage-dir",directory::toString);}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired HomeworkTypeCatalog catalog;
    @Autowired HomeworkPdfImportRepository imports;
    @Autowired HomeworkPdfQuestionRepository questions;
    @Autowired HomeworkRepository homeworks;
    @Autowired HomeworkProblemRepository problems;
    @Autowired StudentHomeworkRepository progress;
    @Autowired HomeworkPdfService service;
    @MockitoBean HomeworkPdfAi ai;
    TransactionTemplate tx;
    Fixture owner;
    byte[] samplePdf;
    record Fixture(Long teacher,Long academy,Long clazz,MockHttpSession session){}
    @BeforeEach void setup() throws Exception {
        samplePdf=pdf(1);
        tx=new TransactionTemplate(manager);owner=fixture(null,TeacherAcademyRole.TEACHER);
        when(ai.ready()).thenReturn(true);when(ai.model()).thenReturn("mock-test");
        when(ai.analyze(any(),anyList())).thenAnswer(invocation->{
            List<Map<String,Object>> candidates=invocation.getArgument(1);
            return new Analysis(true,List.of(new DetectedQuestion("101","첫 문항의 조건과 수식",List.of(new Region(1,0,0,1,0.4)),(String)candidates.getFirst().get("id"),"문항 조건에 해당",false),
                    new DetectedQuestion("102","둘째 문항의 조건과 수식",List.of(new Region(1,0,0.4,1,0.4)),null,"직접 확인 필요",true)));
        });
    }
    Fixture fixture(Long academyId,TeacherAcademyRole role){return tx.execute(status->{
        var academy=academyId==null?Academy.builder().name("가상 PDF 학원").build():em.find(Academy.class,academyId);
        if(academyId==null)em.persist(academy);
        var teacher=Teacher.builder().name("가상 교사").username("pdf-"+UUID.randomUUID()).pin("123456").build();em.persist(teacher);
        em.persist(TeacherAcademy.builder().teacherId(teacher.getId()).academyId(academy.getId()).role(role).build());
        var clazz=AcademyClass.builder().name("가상 PDF 반").academy(academy).ownerTeacherId(teacher.getId()).build();em.persist(clazz);em.flush();
        var session=new MockHttpSession();session.setAttribute("userId",teacher.getId());session.setAttribute("userRole","TEACHER");session.setAttribute("activeAcademyId",academy.getId());session.setAttribute("activeRole",role.name());
        return new Fixture(teacher.getId(),academy.getId(),clazz.getId(),session);
    });}
    static byte[] pdf(int pages)throws Exception{try(var document=new PDDocument();var out=new ByteArrayOutputStream()){for(int i=0;i<pages;i++)document.addPage(new PDPage());document.save(out);return out.toByteArray();}}
    JsonNode upload(Fixture who,String key)throws Exception{
        return mapper.readTree(mvc.perform(multipart("/api/homeworks/pdf-imports").file(new MockMultipartFile("file","sample.pdf","application/pdf",samplePdf.clone()))
                .param("classId",who.clazz().toString()).param("subjectId","ALG").param("requestKey",key).param("consent","true").session(who.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode ready(String id)throws Exception{
        for(int attempt=0;attempt<100;attempt++){
            JsonNode result=mapper.readTree(mvc.perform(get("/api/homeworks/pdf-imports/{id}",id).session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if(!result.path("state").asText().equals("PROCESSING"))return result;
            Thread.sleep(30);
        }
        throw new AssertionError("Analysis did not finish");
    }
    ObjectNode registration(JsonNode view){
        var request=mapper.createObjectNode().put("expectedVersion",view.path("version").asLong()).put("catalogVersion",catalog.version()).put("title","PDF 테스트 숙제").put("contentsReviewed",true);
        var rows=request.putArray("questions");
        for(var source:view.path("questions")){
            var row=rows.addObject().put("id",source.path("id").asText()).put("sourceNumber",source.path("sourceNumber").asText()).put("text",source.path("text").asText());
            row.set("regions",source.path("regions"));row.set("typeId",source.path("suggestedTypeId"));
        }
        return request;
    }
    JsonNode register(JsonNode view,JsonNode request)throws Exception{return mapper.readTree(mvc.perform(post("/api/homeworks/pdf-imports/{id}/register",view.path("id").asText())
            .session(owner.session()).contentType("application/json").content(mapper.writeValueAsBytes(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

    @Test void registers_once_and_keeps_fixed_question_ids_and_original_progress()throws Exception{
        Long oldProgress=tx.execute(status->{
            var clazz=em.find(AcademyClass.class,owner.clazz());
            var old=Homework.builder().title("기존 숙제").questionCount(2).academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(old);
            var student=Student.builder().name("가상 학생").grade("고1").school("가상 학교").academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(student);
            var record=StudentHomework.builder().student(student).homework(old).incorrectCount(1).unsolvedCount(0).incorrectQuestions("2").build();em.persist(record);em.flush();return record.getId();
        });
        long before=homeworks.count();var started=upload(owner,UUID.randomUUID().toString());var view=ready(started.path("id").asText());
        assertThat(view.path("state").asText()).isEqualTo("REVIEW");var request=registration(view);var saved=register(view,request);
        assertThat(saved.path("state").asText()).isEqualTo("REGISTERED");assertThat(homeworks.count()).isEqualTo(before+1);
        assertThat(saved.path("questions").get(0).path("id").asText()).isEqualTo(view.path("questions").get(0).path("id").asText());
        assertThat(saved.path("questions").get(1).path("confirmedTypeId").isNull()).isTrue();
        var slots=problems.findByHomeworkIdOrderByPositionAsc(saved.path("homeworkId").asLong());assertThat(slots).extracting(HomeworkProblem::getPosition).containsExactly(1,2);
        assertThat(register(view,request).path("homeworkId")).isEqualTo(saved.path("homeworkId"));assertThat(homeworks.count()).isEqualTo(before+1);
        assertThat(progress.findById(oldProgress).orElseThrow().getIncorrectQuestions()).isEqualTo("2");
        mvc.perform(put("/api/homeworks/{id}/problems",saved.path("homeworkId").asLong()).session(owner.session()).contentType("application/json").content("[]")).andExpect(status().isBadRequest());
        assertThat(problems.findByHomeworkIdOrderByPositionAsc(saved.path("homeworkId").asLong())).extracting(HomeworkProblem::getId).containsExactlyElementsOf(slots.stream().map(HomeworkProblem::getId).toList());
        Long anotherClass=tx.execute(status->{var clazz=AcademyClass.builder().name("같은 교사의 다른 반").academy(em.find(Academy.class,owner.academy())).ownerTeacherId(owner.teacher()).build();em.persist(clazz);em.flush();return clazz.getId();});
        for(var edit:List.of(Map.of("title","PDF 테스트 숙제","questionCount",3,"classId",owner.clazz()),Map.of("title","PDF 테스트 숙제","questionCount",2,"classId",anotherClass))) {
            mvc.perform(put("/api/homeworks/{id}",saved.path("homeworkId").asLong()).session(owner.session()).contentType("application/json").content(mapper.writeValueAsBytes(edit))).andExpect(status().isBadRequest());
        }
        assertThat(homeworks.findById(saved.path("homeworkId").asLong()).orElseThrow().getQuestionCount()).isEqualTo(2);
    }
    @Test void repeated_upload_key_does_not_repeat_ai_call()throws Exception{
        String key=UUID.randomUUID().toString();var first=upload(owner,key);ready(first.path("id").asText());var again=upload(owner,key);
        assertThat(again.path("id")).isEqualTo(first.path("id"));verify(ai,times(1)).analyze(any(),anyList());
    }
    @Test void unknown_ai_type_is_left_unclassified_not_silently_substituted()throws Exception{
        doReturn(new Analysis(false,List.of(new DetectedQuestion("1","가상 문항",List.of(new Region(1,0,0,1,1)),"invented-id","wrong",false)))).when(ai).analyze(any(),anyList());
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());
        assertThat(view.path("reviewRequired").asBoolean()).isTrue();assertThat(view.path("questions").get(0).path("suggestedTypeId").isNull()).isTrue();
        assertThat(view.path("questions").get(0).path("needsReview").asBoolean()).isTrue();
    }
    @Test void stale_versions_and_unknown_confirmed_types_create_no_homework()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());long before=homeworks.count();var request=registration(view);
        request.put("expectedVersion",-1);
        mvc.perform(post("/api/homeworks/pdf-imports/{id}/register",view.path("id").asText()).session(owner.session()).contentType("application/json").content(request.toString())).andExpect(status().isConflict());
        request.put("expectedVersion",view.path("version").asLong());((ObjectNode)request.path("questions").get(1)).put("typeId","made-up");
        mvc.perform(post("/api/homeworks/pdf-imports/{id}/register",view.path("id").asText()).session(owner.session()).contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        assertThat(homeworks.count()).isEqualTo(before);
    }
    @Test void rejects_other_teachers_other_academies_and_assistants()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());var other=fixture(owner.academy(),TeacherAcademyRole.TEACHER);var foreign=fixture(null,TeacherAcademyRole.TEACHER);var assistant=fixture(owner.academy(),TeacherAcademyRole.ASSISTANT);
        for(var who:List.of(other,foreign)){
            mvc.perform(get("/api/homeworks/pdf-imports/{id}",view.path("id").asText()).session(who.session())).andExpect(status().isNotFound());
            mvc.perform(get("/api/homeworks/pdf-imports/{id}/document",view.path("id").asText()).session(who.session())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/homeworks/pdf-config").session(assistant.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/homeworks/pdf-config")).andExpect(status().isUnauthorized());
    }
    @Test void cancel_and_expiry_remove_only_drafts_and_never_registered_files()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());
        var saved=register(view,registration(view));
        String savedId=saved.path("id").asText();var draft=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());String draftId=draft.path("id").asText();
        mvc.perform(delete("/api/homeworks/pdf-imports/{id}",draftId).session(owner.session())).andExpect(status().isNoContent());
        tx.executeWithoutResult(status->{var job=imports.findById(savedId).orElseThrow();job.setExpiresAt(LocalDateTime.of(2000,1,1,0,0));imports.save(job);});
        service.purgeDrafts();assertThat(Files.exists(directory.resolve(draftId+".pdf"))).isFalse();assertThat(Files.exists(directory.resolve(savedId+".pdf"))).isTrue();
    }
    @Test void rejects_invalid_pdfs_before_ai_and_does_not_retry_ai_failure()throws Exception{
        mvc.perform(multipart("/api/homeworks/pdf-imports").file(new MockMultipartFile("file","fake.pdf","application/pdf",new byte[]{1,2,3,4,5}))
                .param("classId",owner.clazz().toString()).param("subjectId","ALG").param("requestKey",UUID.randomUUID().toString()).param("consent","true").session(owner.session())).andExpect(status().isBadRequest());
        verify(ai,never()).analyze(any(),anyList());doThrow(new IllegalStateException("mock failure")).when(ai).analyze(any(),anyList());
        var result=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());assertThat(result.path("state").asText()).isEqualTo("FAILED");verify(ai,times(1)).analyze(any(),anyList());
    }
}
