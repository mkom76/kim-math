package com.example.controller;

import com.example.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubmissionErrorCausesControllerTest {
    private static final String COUNTS = """
            {"calculationMistake":0,"conceptGap":2,"conditionMisread":1,"ideaGap":0,"timeManagement":1}
            """;
    @Autowired private MockMvc mvc;
    @PersistenceContext private EntityManager em;
    private Academy academy;
    private AcademyClass clazz;
    private Teacher teacher;
    private Student student;
    private StudentSubmission submission;
    private TestQuestion essay;
    private com.example.entity.Test exam;

    @BeforeEach
    void setUp() {
        academy = persist(Academy.builder().name("원인 분석 학원").build());
        teacher = teacher("owner", academy);
        clazz = persist(AcademyClass.builder().name("고1").academy(academy).ownerTeacherId(teacher.getId()).build());
        student = persist(Student.builder().name("학생").grade("고1").school("학교")
                .academy(academy).academyClass(clazz).build());
        persist(StudentClassEnrollment.builder().student(student).academyClass(clazz)
                .status(StudentClassEnrollmentStatus.ACTIVE).startedAt(LocalDateTime.now().minusMonths(1)).build());
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(clazz)
                .lessonDate(LocalDate.of(2026, 10, 12)).build());
        exam = persist(com.example.entity.Test.builder().title("1회차").academy(academy).academyClass(clazz).lesson(lesson).build());
        TestQuestion question = persist(TestQuestion.builder().test(exam).number(1).answer("3")
                .points(50.0).questionType(QuestionType.OBJECTIVE).build());
        essay = persist(TestQuestion.builder().test(exam).number(2).answer("풀이")
                .points(50.0).questionType(QuestionType.ESSAY).build());
        submission = StudentSubmission.builder().student(student).test(exam).totalScore(50)
                .submittedAt(LocalDateTime.of(2026, 10, 12, 19, 0)).build();
        submission.getDetails().add(StudentSubmissionDetail.builder().submission(submission).question(question)
                .studentAnswer("3").isCorrect(true).earnedPoints(50.0).build());
        submission.getDetails().add(StudentSubmissionDetail.builder().submission(submission).question(essay)
                .studentAnswer("풀이").build());
        persist(submission);
        em.flush();
    }

    @Test
    void preservesZeroAndAllowsOverlappingCountsWithoutChangingScoreOrSubmissionTime() throws Exception {
        LocalDateTime submittedAt = submission.getSubmittedAt();
        save(COUNTS).andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationMistake").value(0))
                .andExpect(jsonPath("$.conceptGap").value(2))
                .andExpect(jsonPath("$.status").value("COMPLETE"));
        em.flush();
        em.clear();
        StudentSubmission stored = em.find(StudentSubmission.class, submission.getId());
        assertThat(stored.getConceptGap()).isEqualTo(2);
        assertThat(stored.getTotalScore()).isEqualTo(50);
        assertThat(stored.getSubmittedAt()).isEqualTo(submittedAt);
    }

    @Test
    void distinguishesPartialAndAllMissingFromZero() throws Exception {
        save(COUNTS.replace("\"ideaGap\":0", "\"ideaGap\":null"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.ideaGap").value(nullValue()));
        save("""
                {"calculationMistake":null,"conceptGap":null,"conditionMisread":null,"ideaGap":null,"timeManagement":null}
                """)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_ENTERED"));
        assertThat(submission.hasErrorCauses()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "2147483648", "\"abc\"", "true"})
    void rejectsInvalidCountsWithoutOverwritingExistingData(String value) throws Exception {
        save(COUNTS).andExpect(status().isOk());
        save(COUNTS.replace("\"calculationMistake\":0", "\"calculationMistake\":" + value))
                .andExpect(status().isBadRequest());
        assertThat(submission.getCalculationMistake()).isZero();
    }

    @Test
    void rejectsPartialPayloadInsteadOfClearingUnsentFields() throws Exception {
        save("{\"calculationMistake\":3}").andExpect(status().isBadRequest());
    }

    @Test
    void studentsCannotWriteAndUnassignedOrForeignTeachersCannotWrite() throws Exception {
        mvc.perform(put("/api/submissions/{id}/error-causes", submission.getId()).session(studentSession())
                        .contentType(MediaType.APPLICATION_JSON).content(COUNTS))
                .andExpect(status().isForbidden());
        Teacher unrelated = teacher("unrelated", academy);
        mvc.perform(put("/api/submissions/{id}/error-causes", submission.getId()).session(teacherSession(unrelated, academy))
                        .contentType(MediaType.APPLICATION_JSON).content(COUNTS))
                .andExpect(status().isForbidden());
        Academy foreignAcademy = persist(Academy.builder().name("다른 학원").build());
        Teacher foreign = teacher("foreign", foreignAcademy);
        mvc.perform(put("/api/submissions/{id}/error-causes", submission.getId()).session(teacherSession(foreign, foreignAcademy))
                        .contentType(MediaType.APPLICATION_JSON).content(COUNTS))
                .andExpect(status().isForbidden());
        assertThat(submission.hasErrorCauses()).isFalse();
    }

    @Test
    void studentReadsOwnCausesAndStableTestDateEvenWhenScoresAreHidden() throws Exception {
        save(COUNTS).andExpect(status().isOk());
        student.setHideScoresFromStudent(true);
        mvc.perform(get("/api/submissions/me/test/{id}", exam.getId()).session(studentSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalScore").value(nullValue()))
                .andExpect(jsonPath("$.errorCauses.conceptGap").value(2))
                .andExpect(jsonPath("$.lessonDate").value("2026-10-12"));
        mvc.perform(get("/api/submissions/student/{id}", student.getId()).session(studentSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].errorCauses.status").value("COMPLETE"))
                .andExpect(jsonPath("$[0].classId").value(clazz.getId()));
        mvc.perform(get("/api/tests/{id}/submission-roster", exam.getId()).session(teacherSession(teacher, academy)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].errorCauses.status").value("COMPLETE"));
    }

    @Test
    void answerCorrectionPreservesCountsAndRequiresReviewUntilSavedAgain() throws Exception {
        save(COUNTS).andExpect(status().isOk());
        mvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}", student.getId(), exam.getId())
                        .session(teacherSession(teacher, academy)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"1\":\"2\",\"2\":\"풀이\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.errorCauses.conceptGap").value(2))
                .andExpect(jsonPath("$.errorCauses.status").value("NEEDS_REVIEW"));
        save(COUNTS).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    @Test
    void recalculationAndEssayGradingRequireReviewWithoutDeletingCounts() throws Exception {
        save(COUNTS).andExpect(status().isOk());
        mvc.perform(post("/api/tests/{id}/recalculate", exam.getId()).session(teacherSession(teacher, academy)))
                .andExpect(status().isOk());
        assertThat(submission.isErrorCausesNeedReview()).isTrue();
        save(COUNTS).andExpect(status().isOk());
        Long detailId = submission.getDetails().stream().filter(d -> d.getQuestion().equals(essay)).findFirst().orElseThrow().getId();
        mvc.perform(put("/api/submissions/details/{id}/grade", detailId).session(teacherSession(teacher, academy))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"earnedPoints\":20}"))
                .andExpect(status().isOk());
        assertThat(submission.isErrorCausesNeedReview()).isTrue();
        assertThat(submission.getConceptGap()).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/submissions/{id}/error-causes", submission.getId())
                .session(teacherSession(teacher, academy)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Teacher teacher(String name, Academy owner) {
        Teacher value = persist(Teacher.builder().name(name).username(name).pin("123456").build());
        persist(TeacherAcademy.builder().teacherId(value.getId()).academyId(owner.getId()).role(TeacherAcademyRole.TEACHER).build());
        return value;
    }

    private MockHttpSession teacherSession(Teacher value, Academy owner) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", value.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", owner.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }

    private MockHttpSession studentSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }

    private <T> T persist(T entity) { em.persist(entity); return entity; }
}
