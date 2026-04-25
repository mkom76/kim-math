package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.QuestionType;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Textbook;
import com.example.entity.TextbookProblem;
import com.example.repository.HomeworkProblemRepository;
import com.example.repository.HomeworkRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeworkProblemsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private HomeworkRepository homeworkRepository;
    @Autowired private HomeworkProblemRepository homeworkProblemRepository;
    @PersistenceContext private EntityManager em;

    private Teacher teacher;
    private Academy academy;
    private Homework homework;
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

        homework = Homework.builder()
                .title("숙제1").questionCount(0)
                .academy(academy).academyClass(clazz).build();
        em.persist(homework);

        Textbook tb = Textbook.builder().ownerTeacherId(teacher.getId()).title("쎈").build();
        em.persist(tb);

        p1 = TextbookProblem.builder()
                .textbook(tb).number(15).answer("3")
                .questionType(QuestionType.OBJECTIVE)
                .topic("일차함수").videoLink("https://y/p1").build();
        em.persist(p1);

        p2 = TextbookProblem.builder()
                .textbook(tb).number(22)
                .questionType(QuestionType.SUBJECTIVE)
                .topic("이차방정식").build();
        em.persist(p2);

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

    @Test
    void put_replaces_problems_with_mixed_manual_and_textbook() throws Exception {
        // 수동 2개 + 교재 2개 = 4개
        String body = "[" +
                "{\"textbookProblemId\":null}," +
                "{\"textbookProblemId\":null}," +
                "{\"textbookProblemId\":" + p1.getId() + "}," +
                "{\"textbookProblemId\":" + p2.getId() + "}" +
                "]";

        mockMvc.perform(put("/api/homeworks/{id}/problems", homework.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].textbookProblem").doesNotExist())
                .andExpect(jsonPath("$[2].textbookProblem.topic").value("일차함수"))
                .andExpect(jsonPath("$[3].textbookProblem.topic").value("이차방정식"));

        var saved = homeworkProblemRepository.findByHomeworkIdOrderByPositionAsc(homework.getId());
        assertThat(saved).hasSize(4);
        assertThat(saved.get(0).getTextbookProblem()).isNull();
        assertThat(saved.get(2).getTextbookProblem().getId()).isEqualTo(p1.getId());
        // questionCount 자동 동기화
        assertThat(homeworkRepository.findById(homework.getId()).orElseThrow().getQuestionCount()).isEqualTo(4);
    }

    @Test
    void put_overwrites_previous_problems() throws Exception {
        mockMvc.perform(put("/api/homeworks/{id}/problems", homework.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("[{\"textbookProblemId\":" + p1.getId() + "}]"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/homeworks/{id}/problems", homework.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("[{\"textbookProblemId\":" + p2.getId() + "}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].textbookProblem.topic").value("이차방정식"));
    }

    @Test
    void put_rejects_other_teachers_textbook_problem() throws Exception {
        Teacher other = new Teacher();
        other.setName("선생B"); other.setUsername("b"); other.setPin("000002");
        em.persist(other);
        Textbook otherTb = Textbook.builder().ownerTeacherId(other.getId()).title("B의 교재").build();
        em.persist(otherTb);
        TextbookProblem otherP = TextbookProblem.builder()
                .textbook(otherTb).number(1).questionType(QuestionType.OBJECTIVE).build();
        em.persist(otherP);
        em.flush();

        mockMvc.perform(put("/api/homeworks/{id}/problems", homework.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("[{\"textbookProblemId\":" + otherP.getId() + "}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returns_empty_list_when_no_problems() throws Exception {
        mockMvc.perform(get("/api/homeworks/{id}/problems", homework.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
