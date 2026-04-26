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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentScoreVisibilityTest {

    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass clazz;
    private Student hiddenStudent;
    private Student visibleStudent;
    private Teacher teacher;
    private com.example.entity.Test exam;

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

        hiddenStudent = Student.builder()
                .name("가린이").grade("중3").school("S")
                .academy(academy).academyClass(clazz)
                .hideScoresFromStudent(true).build();
        em.persist(hiddenStudent);

        visibleStudent = Student.builder()
                .name("보임이").grade("중3").school("S")
                .academy(academy).academyClass(clazz)
                .hideScoresFromStudent(false).build();
        em.persist(visibleStudent);

        exam = com.example.entity.Test.builder()
                .title("3월 시험").academy(academy).academyClass(clazz).build();
        em.persist(exam);

        TestQuestion q1 = TestQuestion.builder()
                .test(exam).number(1).answer("3").points(100.0)
                .questionType(QuestionType.OBJECTIVE).build();
        em.persist(q1);

        StudentSubmission subHidden = StudentSubmission.builder()
                .student(hiddenStudent).test(exam).totalScore(70).build();
        subHidden.getDetails().add(StudentSubmissionDetail.builder()
                .submission(subHidden).question(q1).studentAnswer("3")
                .isCorrect(true).earnedPoints(100.0).build());
        em.persist(subHidden);

        StudentSubmission subVisible = StudentSubmission.builder()
                .student(visibleStudent).test(exam).totalScore(90).build();
        subVisible.getDetails().add(StudentSubmissionDetail.builder()
                .submission(subVisible).question(q1).studentAnswer("3")
                .isCorrect(true).earnedPoints(100.0).build());
        em.persist(subVisible);

        em.flush();
    }

    private MockHttpSession studentSession(Student s) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", s.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }

    @Test
    void hidden_student_sees_null_totalScore_in_own_result() throws Exception {
        mockMvc.perform(get("/api/submissions/me/test/{testId}", exam.getId())
                        .session(studentSession(hiddenStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true));
    }

    @Test
    void visible_student_sees_normal_totalScore() throws Exception {
        mockMvc.perform(get("/api/submissions/me/test/{testId}", exam.getId())
                        .session(studentSession(visibleStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(90));
    }

    @Test
    void hidden_student_sees_null_in_my_submissions_list() throws Exception {
        mockMvc.perform(get("/api/submissions/student/{studentId}", hiddenStudent.getId())
                        .session(studentSession(hiddenStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalScore").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].classAverage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].rank").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void teacher_sees_real_totalScore_for_hidden_student() throws Exception {
        mockMvc.perform(get("/api/submissions")
                        .param("studentId", String.valueOf(hiddenStudent.getId()))
                        .param("testId", String.valueOf(exam.getId()))
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(70));
    }

    @Test
    void teacher_can_toggle_score_visibility() throws Exception {
        mockMvc.perform(put("/api/students/{id}/score-visibility", visibleStudent.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"hide\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hideScoresFromStudent").value(true));

        // 변경 후 학생 본인 조회 시 마스킹 적용
        mockMvc.perform(get("/api/submissions/me/test/{testId}", exam.getId())
                        .session(studentSession(visibleStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void student_cannot_toggle_score_visibility() throws Exception {
        mockMvc.perform(put("/api/students/{id}/score-visibility", hiddenStudent.getId())
                        .session(studentSession(hiddenStudent))
                        .contentType("application/json")
                        .content("{\"hide\":false}"))
                .andExpect(status().isForbidden());
    }
}
