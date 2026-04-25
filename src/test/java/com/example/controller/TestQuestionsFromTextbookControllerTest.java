package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Textbook;
import com.example.entity.TextbookProblem;
import com.example.repository.TestQuestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TestQuestionsFromTextbookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestQuestionRepository testQuestionRepository;
    @PersistenceContext private EntityManager em;

    private Teacher teacher;
    private Academy academy;
    private com.example.entity.Test test;
    private TextbookProblem p1;
    private TextbookProblem p2;

    @BeforeEach
    void setUp() {
        academy = new Academy();
        academy.setName("학원A");
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A"); teacher.setUsername("a"); teacher.setPin("000001");
        em.persist(teacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER).build());

        AcademyClass clazz = AcademyClass.builder()
                .name("반1").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(clazz);

        test = com.example.entity.Test.builder().title("시험1").academy(academy).academyClass(clazz).build();
        em.persist(test);

        Textbook tb = Textbook.builder().ownerTeacherId(teacher.getId()).title("쎈").build();
        em.persist(tb);

        p1 = TextbookProblem.builder()
                .textbook(tb).number(15).answer("3")
                .questionType(QuestionType.OBJECTIVE)
                .topic("일차함수").videoLink("https://y/p1").build();
        em.persist(p1);

        p2 = TextbookProblem.builder()
                .textbook(tb).number(22).answer("x=5")
                .questionType(QuestionType.SUBJECTIVE)
                .topic("이차방정식").videoLink("https://y/p2").build();
        em.persist(p2);

        em.flush();
        TenantContext.set(teacher.getId(), academy.getId(), TeacherAcademyRole.TEACHER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
    void from_textbook_snapshots_answer_and_question_type_and_sets_fk() throws Exception {
        String body = "[" +
                "{\"textbookProblemId\":" + p1.getId() + ",\"number\":1,\"points\":5}," +
                "{\"textbookProblemId\":" + p2.getId() + ",\"number\":2,\"points\":10}" +
                "]";

        mockMvc.perform(post("/api/tests/{id}/questions/from-textbook", test.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        var saved = testQuestionRepository.findByTestIdOrderByNumber(test.getId());
        assertThat(saved).hasSize(2);
        // 1번 - p1 스냅샷
        assertThat(saved.get(0).getNumber()).isEqualTo(1);
        assertThat(saved.get(0).getAnswer()).isEqualTo("3");
        assertThat(saved.get(0).getPoints()).isEqualTo(5.0);
        assertThat(saved.get(0).getQuestionType()).isEqualTo(QuestionType.OBJECTIVE);
        assertThat(saved.get(0).getTextbookProblem()).isNotNull();
        assertThat(saved.get(0).getTextbookProblem().getId()).isEqualTo(p1.getId());
        // 2번 - p2 스냅샷
        assertThat(saved.get(1).getAnswer()).isEqualTo("x=5");
        assertThat(saved.get(1).getQuestionType()).isEqualTo(QuestionType.SUBJECTIVE);
    }

    @Test
    void getTestQuestions_response_includes_textbookProblem_metadata() throws Exception {
        String body = "[{\"textbookProblemId\":" + p1.getId() + ",\"number\":1,\"points\":5}]";
        mockMvc.perform(post("/api/tests/{id}/questions/from-textbook", test.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tests/{id}/questions", test.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].textbookProblem.topic").value("일차함수"))
                .andExpect(jsonPath("$[0].textbookProblem.videoLink").value("https://y/p1"));
    }

    @Test
    void cannot_use_textbook_problem_owned_by_other_teacher() throws Exception {
        // 다른 선생의 교재 만들기
        Teacher other = new Teacher();
        other.setName("선생B"); other.setUsername("b"); other.setPin("000002");
        em.persist(other);
        Textbook otherTb = Textbook.builder().ownerTeacherId(other.getId()).title("B의 교재").build();
        em.persist(otherTb);
        TextbookProblem otherP = TextbookProblem.builder()
                .textbook(otherTb).number(1).answer("?").questionType(QuestionType.OBJECTIVE).build();
        em.persist(otherP);
        em.flush();

        String body = "[{\"textbookProblemId\":" + otherP.getId() + ",\"number\":1,\"points\":5}]";
        mockMvc.perform(post("/api/tests/{id}/questions/from-textbook", test.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
