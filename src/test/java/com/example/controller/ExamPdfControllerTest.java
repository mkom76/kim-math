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
import static com.example.service.homeworkpdf.ExamPdfTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.homework-pdf.enabled=true","app.homework-pdf.global-daily-limit=100",
        "spring.datasource.url=jdbc:h2:mem:exam_pdf;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ExamPdfControllerTest {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("kim-math-pdf-test-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.homework-pdf.storage-dir",directory::toString);}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired HomeworkTypeCatalog catalog;
    @Autowired ExamPdfImportRepository imports;
    @Autowired ExamPdfQuestionRepository questions;
    @Autowired TestRepository exams;
    @Autowired TestQuestionRepository problems;
    @Autowired StudentHomeworkRepository progress;
    @Autowired StudentSubmissionRepository submissions;
    @Autowired ExamPdfService service;
    @MockitoBean ExamPdfAi ai;
    TransactionTemplate tx;
    Fixture owner;
    byte[] samplePdf;
    record Fixture(Long teacher,Long academy,Long clazz,MockHttpSession session){}
    @BeforeEach void setup() throws Exception {
        samplePdf=pdf(2);
        tx=new TransactionTemplate(manager);owner=fixture(null,TeacherAcademyRole.TEACHER);
        when(ai.ready()).thenReturn(true);when(ai.model()).thenReturn("mock-test");
        when(ai.analyze(any(),anyList(),anyInt())).thenAnswer(invocation->{
            List<Map<String,Object>> candidates=invocation.getArgument(1);
            return new Analysis(true,List.of(new DetectedQuestion("101","첫 문항의 조건과 수식",List.of(new Region(1,0,0,1,0.4)),(String)candidates.getFirst().get("id"),"문항 조건에 해당",false,"2",5.0,"SUBJECTIVE",false),
                    new DetectedQuestion("102","둘째 문항의 조건과 수식",List.of(new Region(1,0,0.4,1,0.4)),null,"직접 확인 필요",true,"3",4.0,"OBJECTIVE",false)));
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
        return mapper.readTree(mvc.perform(multipart("/api/tests/pdf-imports").file(new MockMultipartFile("file","sample.pdf","application/pdf",samplePdf.clone()))
                .param("classId",who.clazz().toString()).param("subjectId","ALG").param("requestKey",key).param("consent","true").param("questionPages","1").param("solutionPages","2").session(who.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode ready(String id)throws Exception{
        for(int attempt=0;attempt<100;attempt++){
            JsonNode result=mapper.readTree(mvc.perform(get("/api/tests/pdf-imports/{id}",id).session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
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
            row.set("answer",source.path("answer"));row.set("points",source.path("points"));row.set("format",source.path("format"));row.set("multipleAnswers",source.path("multipleAnswers"));row.set("regions",source.path("regions"));row.set("typeId",source.path("suggestedTypeId"));
        }
        return request;
    }
    JsonNode register(JsonNode view,JsonNode request)throws Exception{return mapper.readTree(mvc.perform(post("/api/tests/pdf-imports/{id}/register",view.path("id").asText())
            .session(owner.session()).contentType("application/json").content(mapper.writeValueAsBytes(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

    @Test void registers_once_and_keeps_fixed_question_ids_and_original_progress()throws Exception{
        Long oldSubmission=tx.execute(status->{
            var clazz=em.find(AcademyClass.class,owner.clazz());
            var old=com.example.entity.Test.builder().title("기존 성적 시험").academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(old);
            var student=Student.builder().name("가상 기존 학생").grade("고1").school("가상 학교").academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(student);
            var result=StudentSubmission.builder().student(student).test(old).totalScore(73).calculationMistake(2).build();em.persist(result);em.flush();return result.getId();
        });
        Long oldProgress=tx.execute(status->{
            var clazz=em.find(AcademyClass.class,owner.clazz());
            var old=Homework.builder().title("기존 숙제").questionCount(2).academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(old);
            var student=Student.builder().name("가상 학생").grade("고1").school("가상 학교").academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(student);
            var record=StudentHomework.builder().student(student).homework(old).incorrectCount(1).unsolvedCount(0).incorrectQuestions("2").build();em.persist(record);em.flush();return record.getId();
        });
        long before=exams.count();var started=upload(owner,UUID.randomUUID().toString());var view=ready(started.path("id").asText());
        assertThat(view.path("state").asText()).isEqualTo("REVIEW");var request=registration(view);var saved=register(view,request);
        assertThat(saved.path("state").asText()).isEqualTo("REGISTERED");assertThat(exams.count()).isEqualTo(before+1);
        assertThat(saved.path("questions").get(0).path("id").asText()).isEqualTo(view.path("questions").get(0).path("id").asText());
        assertThat(saved.path("questions").get(1).path("confirmedTypeId").isNull()).isTrue();
        var slots=problems.findByTestIdOrderByNumber(saved.path("testId").asLong());assertThat(slots).extracting(TestQuestion::getNumber).containsExactly(1,2);
        assertThat(register(view,request).path("testId")).isEqualTo(saved.path("testId"));assertThat(exams.count()).isEqualTo(before+1);
        assertThat(progress.findById(oldProgress).orElseThrow().getIncorrectQuestions()).isEqualTo("2");
        assertThat(submissions.findById(oldSubmission).orElseThrow().getTotalScore()).isEqualTo(73);
        assertThat(submissions.findById(oldSubmission).orElseThrow().getCalculationMistake()).isEqualTo(2);
        long testId=saved.path("testId").asLong();
        Long newSubmission=tx.execute(status->{
            var clazz=em.find(AcademyClass.class,owner.clazz());
            var student=Student.builder().name("가상 PDF 학생").grade("고1").school("가상 학교").academy(clazz.getAcademy()).academyClass(clazz).build();em.persist(student);
            var result=StudentSubmission.builder().student(student).test(em.find(com.example.entity.Test.class,testId)).totalScore(0).build();em.persist(result);
            for(var slot:slots){var detail=StudentSubmissionDetail.builder().submission(result).question(em.find(TestQuestion.class,slot.getId())).studentAnswer("4").isCorrect(false).earnedPoints(0.0).build();em.persist(detail);}
            em.flush();return result.getId();
        });
        mvc.perform(delete("/api/tests/{id}",testId).session(owner.session())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tests/{id}/questions",testId).session(owner.session()).contentType("application/json").content("{\"number\":3}" )).andExpect(status().isBadRequest());
        var changed=mapper.createObjectNode();var expected=changed.putArray("expectedQuestionIds");slots.forEach(q->expected.add(q.getId()));
        var answers=changed.putArray("answers");
        for(var slot:slots){var q=answers.addObject().put("id",slot.getId()).put("number",slot.getNumber()).put("answer","4").put("points",6).put("questionType","SUBJECTIVE");if(slot.getTopic()==null)q.putNull("topic");else q.put("topic",slot.getTopic());}
        mvc.perform(put("/api/tests/{id}/answers",testId).session(owner.session()).contentType("application/json").content(changed.toString())).andExpect(status().isOk());
        var updated=mapper.readTree(mvc.perform(get("/api/tests/pdf-imports/{id}",saved.path("id").asText()).session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.path("questions").get(0).path("answer").asText()).isEqualTo("4");
        assertThat(submissions.findById(newSubmission).orElseThrow().getTotalScore()).isEqualTo(100);
        assertThat(submissions.findById(oldSubmission).orElseThrow().getTotalScore()).isEqualTo(73);
        assertThat(problems.findByTestIdOrderByNumber(testId)).extracting(TestQuestion::getId).containsExactlyElementsOf(slots.stream().map(TestQuestion::getId).toList());
        ((ObjectNode)answers.get(0)).put("number",99);
        mvc.perform(put("/api/tests/{id}/answers",testId).session(owner.session()).contentType("application/json").content(changed.toString())).andExpect(status().isBadRequest());
    }
    @Test void repeated_upload_key_does_not_repeat_ai_call()throws Exception{
        String key=UUID.randomUUID().toString();var first=upload(owner,key);ready(first.path("id").asText());var again=upload(owner,key);
        assertThat(again.path("id")).isEqualTo(first.path("id"));verify(ai,times(1)).analyze(any(),anyList(),anyInt());
    }
    @Test void unknown_ai_type_is_left_unclassified_not_silently_substituted()throws Exception{
        doReturn(new Analysis(false,List.of(new DetectedQuestion("1","가상 문항",List.of(new Region(1,0,0,1,1)),"invented-id","wrong",false,null,null,null,false)))).when(ai).analyze(any(),anyList(),anyInt());
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());
        assertThat(view.path("reviewRequired").asBoolean()).isTrue();assertThat(view.path("questions").get(0).path("suggestedTypeId").isNull()).isTrue();
        assertThat(view.path("questions").get(0).path("needsReview").asBoolean()).isTrue();
    }
    @Test void stale_versions_and_unknown_confirmed_types_create_no_homework()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());long before=exams.count();var request=registration(view);
        request.put("expectedVersion",-1);
        mvc.perform(post("/api/tests/pdf-imports/{id}/register",view.path("id").asText()).session(owner.session()).contentType("application/json").content(request.toString())).andExpect(status().isConflict());
        request.put("expectedVersion",view.path("version").asLong());((ObjectNode)request.path("questions").get(1)).put("typeId","made-up");
        mvc.perform(post("/api/tests/pdf-imports/{id}/register",view.path("id").asText()).session(owner.session()).contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        assertThat(exams.count()).isEqualTo(before);
    }
    @Test void rejects_other_teachers_other_academies_and_assistants()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());var other=fixture(owner.academy(),TeacherAcademyRole.TEACHER);var foreign=fixture(null,TeacherAcademyRole.TEACHER);var assistant=fixture(owner.academy(),TeacherAcademyRole.ASSISTANT);
        for(var who:List.of(other,foreign)){
            mvc.perform(get("/api/tests/pdf-imports/{id}",view.path("id").asText()).session(who.session())).andExpect(status().isNotFound());
            mvc.perform(get("/api/tests/pdf-imports/{id}/document",view.path("id").asText()).session(who.session())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/tests/pdf-config").session(assistant.session())).andExpect(status().isForbidden());
        var student=new MockHttpSession();student.setAttribute("userId",1L);student.setAttribute("userRole","STUDENT");
        mvc.perform(get("/api/tests/pdf-imports/{id}/document",view.path("id").asText()).session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tests/pdf-config")).andExpect(status().isUnauthorized());
    }
    @Test void cancel_and_expiry_remove_only_drafts_and_never_registered_files()throws Exception{
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());
        var saved=register(view,registration(view));
        String savedId=saved.path("id").asText();var draft=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());String draftId=draft.path("id").asText();
        mvc.perform(delete("/api/tests/pdf-imports/{id}",draftId).session(owner.session())).andExpect(status().isNoContent());
        tx.executeWithoutResult(status->{var job=imports.findById(savedId).orElseThrow();job.setExpiresAt(LocalDateTime.of(2000,1,1,0,0));imports.save(job);});
        service.purgeDrafts();assertThat(Files.exists(directory.resolve(draftId+".pdf"))).isFalse();assertThat(Files.exists(directory.resolve(savedId+".pdf"))).isTrue();
    }
    @Test void rejects_invalid_pdfs_before_ai_and_does_not_retry_ai_failure()throws Exception{
        mvc.perform(multipart("/api/tests/pdf-imports").file(new MockMultipartFile("file","fake.pdf","application/pdf",new byte[]{1,2,3,4,5}))
                .param("classId",owner.clazz().toString()).param("subjectId","ALG").param("requestKey",UUID.randomUUID().toString()).param("consent","true").param("questionPages","1").param("solutionPages","2").session(owner.session())).andExpect(status().isBadRequest());
        verify(ai,never()).analyze(any(),anyList(),anyInt());doThrow(new IllegalStateException("mock failure")).when(ai).analyze(any(),anyList(),anyInt());
        var result=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());assertThat(result.path("state").asText()).isEqualTo("FAILED");verify(ai,times(1)).analyze(any(),anyList(),anyInt());
    }

    @Test void missing_answers_or_points_require_teacher_correction_before_any_test_is_created()throws Exception{
        doReturn(new Analysis(true,List.of(new DetectedQuestion("1","가상 문항",List.of(new Region(1,0,0,1,1)),null,"확인 필요",false,null,null,null,false)))).when(ai).analyze(any(),anyList(),anyInt());
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());
        assertThat(view.path("questions").get(0).path("needsReview").asBoolean()).isTrue();
        long before=exams.count();var request=registration(view);
        mvc.perform(post("/api/tests/pdf-imports/{id}/register",view.path("id").asText()).session(owner.session()).contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        assertThat(exams.count()).isEqualTo(before);
        ((ObjectNode)request.path("questions").get(0)).put("answer","7").put("points",3.5).put("format","SUBJECTIVE");
        var saved=register(view,request);
        assertThat(problems.findByTestIdOrderByNumber(saved.path("testId").asLong()).getFirst().getAnswer()).isEqualTo("7");
    }
    @Test void fractional_type_labels_are_preserved_in_question_levels()throws Exception{
        var type=catalog.candidates("ALG").stream().filter(item->item.get("path").toString().contains("/")).findFirst().orElseThrow();
        var view=ready(upload(owner,UUID.randomUUID().toString()).path("id").asText());var request=registration(view);
        ((ObjectNode)request.path("questions").get(0)).put("typeId",(String)type.get("id"));
        var saved=register(view,request);var slot=problems.findByTestIdOrderByNumber(saved.path("testId").asLong()).getFirst();
        var path=(List<?>)type.get("path");
        assertThat(List.of(slot.getTopicL1(),slot.getTopicL2(),slot.getTopicL3(),slot.getTopicL4())).containsExactlyElementsOf(path.stream().map(Object::toString).toList());
        assertThat(slot.getTopic()).isEqualTo(String.join(" › ",path.stream().map(Object::toString).toList()));
    }
    @Test void overlapping_pages_are_rejected_without_ai_and_solution_regions_are_rejected()throws Exception{
        mvc.perform(multipart("/api/tests/pdf-imports").file(new MockMultipartFile("file","sample.pdf","application/pdf",samplePdf.clone()))
                .param("classId",owner.clazz().toString()).param("subjectId","ALG").param("requestKey",UUID.randomUUID().toString()).param("consent","true").param("questionPages","1-2").param("solutionPages","2").session(owner.session())).andExpect(status().isBadRequest());
        verify(ai,never()).analyze(any(),anyList(),anyInt());
        doReturn(new Analysis(true,List.of(new DetectedQuestion("1","해설을 문항으로 잘못 인식",List.of(new Region(2,0,0,1,1)),null,"",true,"2",5.0,"SUBJECTIVE",false)))).when(ai).analyze(any(),anyList(),anyInt());
        assertThat(ready(upload(owner,UUID.randomUUID().toString()).path("id").asText()).path("state").asText()).isEqualTo("FAILED");
    }
}
