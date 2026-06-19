package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.StudentSubmissionDetail;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.TestQuestion;
import com.example.repository.StudentSubmissionRepository;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherSubmissionEntryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentSubmissionRepository submissionRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass clazz;
    private AcademyClass otherClazz;
    private Student studentA;
    private Student studentB;
    private Student otherClassStudent;
    private Teacher teacher;
    private com.example.entity.Test exam;
    private TestQuestion q1;
    private TestQuestion q2;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("t1"); teacher.setPin("000001");
        em.persist(teacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER).build());

        clazz = AcademyClass.builder().name("반1").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(clazz);
        otherClazz = AcademyClass.builder().name("반2").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(otherClazz);

        studentA = Student.builder()
                .name("김민수").grade("중3").school("학교A")
                .academy(academy).academyClass(clazz).build();
        em.persist(studentA);
        studentB = Student.builder()
                .name("박지윤").grade("중3").school("학교A")
                .academy(academy).academyClass(clazz).build();
        em.persist(studentB);
        otherClassStudent = Student.builder()
                .name("이서준").grade("중3").school("학교B")
                .academy(academy).academyClass(otherClazz).build();
        em.persist(otherClassStudent);

        exam = com.example.entity.Test.builder()
                .title("3월 시험").academy(academy).academyClass(clazz).build();
        em.persist(exam);

        q1 = TestQuestion.builder()
                .test(exam).number(1).answer("3").points(50.0)
                .questionType(QuestionType.OBJECTIVE).build();
        em.persist(q1);
        q2 = TestQuestion.builder()
                .test(exam).number(2).answer("x=2").points(50.0)
                .questionType(QuestionType.SUBJECTIVE).build();
        em.persist(q2);

        StudentSubmission submitted = StudentSubmission.builder()
                .student(studentB).test(exam).totalScore(50).build();
        submitted.getDetails().add(StudentSubmissionDetail.builder()
                .submission(submitted).question(q1).studentAnswer("3")
                .isCorrect(true).earnedPoints(50.0).build());
        submitted.getDetails().add(StudentSubmissionDetail.builder()
                .submission(submitted).question(q2).studentAnswer("x=1")
                .isCorrect(false).earnedPoints(0.0).build());
        em.persist(submitted);

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

    private MockHttpSession studentSession(Student student) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }

    @Test
    void roster_includes_unsubmitted_students_in_test_class_only() throws Exception {
        mockMvc.perform(get("/api/tests/{id}/submission-roster", exam.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].studentName", contains("김민수", "박지윤")))
                .andExpect(jsonPath("$[0].submitted").value(false))
                .andExpect(jsonPath("$[0].submissionId").value(nullValue()))
                .andExpect(jsonPath("$[1].submitted").value(true))
                .andExpect(jsonPath("$[1].score").value(50));
    }

    @Test
    void teacher_can_save_answers_for_student() throws Exception {
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), exam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"1\":\"3\",\"2\":\"x=2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.id").value(studentA.getId()))
                .andExpect(jsonPath("$.totalScore").value(100))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[0].studentAnswer").value("3"))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true))
                .andExpect(jsonPath("$.details[1].studentAnswer").value("x=2"))
                .andExpect(jsonPath("$.details[1].isCorrect").value(true));
    }

    @Test
    void student_cannot_submit_for_another_student() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("testId", String.valueOf(exam.getId()))
                        .session(studentSession(studentA))
                        .contentType("application/json")
                        .content("{\"1\":\"3\",\"2\":\"x=2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_cannot_save_answers_for_student_outside_test_class() throws Exception {
        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        otherClassStudent.getId(), exam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"1\":\"3\",\"2\":\"x=2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_answer_update_preserves_essay_grade_only_when_answer_is_unchanged() throws Exception {
        com.example.entity.Test essayExam = com.example.entity.Test.builder()
                .title("서술형 시험").academy(academy).academyClass(clazz).build();
        em.persist(essayExam);
        TestQuestion essay = TestQuestion.builder()
                .test(essayExam).number(1).answer(null).points(100.0)
                .questionType(QuestionType.ESSAY).build();
        em.persist(essay);
        em.flush();

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"1\":\"풀이\"}"))
                .andExpect(status().isOk());

        StudentSubmission submission = submissionRepository
                .findByStudentIdAndTestId(studentA.getId(), essayExam.getId())
                .orElseThrow();
        Long detailId = submission.getDetails().get(0).getId();

        mockMvc.perform(put("/api/submissions/details/{detailId}/grade", detailId)
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"earnedPoints\":80,\"teacherComment\":\"좋아요\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"1\":\"풀이\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(80))
                .andExpect(jsonPath("$.details[0].earnedPoints").value(80.0))
                .andExpect(jsonPath("$.details[0].teacherComment").value("좋아요"));

        mockMvc.perform(put("/api/submissions/students/{studentId}/tests/{testId}",
                        studentA.getId(), essayExam.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"1\":\"다른 풀이\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(0))
                .andExpect(jsonPath("$.details[0].earnedPoints").value(nullValue()))
                .andExpect(jsonPath("$.details[0].teacherComment").value(nullValue()));
    }
}
