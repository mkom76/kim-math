package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.StudentSubmissionDetail;
import com.example.entity.Teacher;
import com.example.entity.TestQuestion;
import com.example.entity.Textbook;
import com.example.entity.TextbookProblem;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentTestResultControllerTest {

    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Student student;
    private com.example.entity.Test exam;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);

        AcademyClass clazz = AcademyClass.builder().name("반1").academy(academy).build();
        em.persist(clazz);

        student = Student.builder()
                .name("학생1").grade("중3").school("학교A")
                .academy(academy).academyClass(clazz).build();
        em.persist(student);

        Teacher teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("t1"); teacher.setPin("000001");
        em.persist(teacher);

        Textbook tb = Textbook.builder().ownerTeacherId(teacher.getId()).title("개념원리").build();
        em.persist(tb);
        TextbookProblem tp = TextbookProblem.builder()
                .textbook(tb).number(1).answer("3")
                .questionType(QuestionType.OBJECTIVE)
                .topic("일차함수").videoLink("https://y/1").build();
        em.persist(tp);

        exam = com.example.entity.Test.builder()
                .title("3월 모의고사")
                .academy(academy).academyClass(clazz).build();
        em.persist(exam);

        TestQuestion q1 = TestQuestion.builder()
                .test(exam).number(1).answer("3").points(50.0)
                .questionType(QuestionType.OBJECTIVE)
                .textbookProblem(tp).build();
        em.persist(q1);

        TestQuestion q2 = TestQuestion.builder()
                .test(exam).number(2).answer("x=2").points(50.0)
                .questionType(QuestionType.SUBJECTIVE).build();
        em.persist(q2);

        StudentSubmission sub = StudentSubmission.builder()
                .student(student).test(exam).totalScore(50).build();
        sub.getDetails().add(StudentSubmissionDetail.builder()
                .submission(sub).question(q1).studentAnswer("3")
                .isCorrect(true).earnedPoints(50.0).build());
        sub.getDetails().add(StudentSubmissionDetail.builder()
                .submission(sub).question(q2).studentAnswer("x=1")
                .isCorrect(false).earnedPoints(0.0).build());
        em.persist(sub);

        em.flush();
    }

    private MockHttpSession studentSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }

    @Test
    void student_can_get_own_result_with_topic_and_video() throws Exception {
        mockMvc.perform(get("/api/submissions/me/test/{testId}", exam.getId())
                        .session(studentSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(50))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].questionNumber").value(1))
                .andExpect(jsonPath("$.details[0].topic").value("일차함수"))
                .andExpect(jsonPath("$.details[0].videoLink").value("https://y/1"))
                .andExpect(jsonPath("$.details[0].isCorrect").value(true))
                .andExpect(jsonPath("$.details[1].topic").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details[1].videoLink").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details[1].isCorrect").value(false));
    }

    @Test
    void student_gets_404_when_not_submitted() throws Exception {
        com.example.entity.Test other = com.example.entity.Test.builder()
                .title("다른 시험")
                .academy(academy).academyClass(student.getAcademyClass()).build();
        em.persist(other);
        em.flush();

        mockMvc.perform(get("/api/submissions/me/test/{testId}", other.getId())
                        .session(studentSession()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(get("/api/submissions/me/test/{testId}", exam.getId()))
                .andExpect(status().isUnauthorized());
    }
}
