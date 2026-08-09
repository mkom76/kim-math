package com.example.controller;

import com.example.config.security.TenantContext;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.QuestionType;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.TestQuestion;
import com.example.repository.TestQuestionRepository;
import com.example.repository.TestRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TestCopyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestRepository testRepository;
    @Autowired private TestQuestionRepository testQuestionRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Teacher teacher;
    private AcademyClass sourceClass;
    private AcademyClass targetClass;
    private com.example.entity.Test sourceTest;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("복사 학원").build();
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("담당 선생님");
        teacher.setUsername("copy-teacher");
        teacher.setPin("123456");
        em.persist(teacher);
        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER)
                .build());

        sourceClass = AcademyClass.builder()
                .name("월수반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        targetClass = AcademyClass.builder()
                .name("화목반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        em.persist(sourceClass);
        em.persist(targetClass);

        sourceTest = com.example.entity.Test.builder()
                .title("중간고사 대비")
                .academy(academy)
                .academyClass(sourceClass)
                .build();
        em.persist(sourceTest);
        em.persist(TestQuestion.builder()
                .test(sourceTest)
                .number(1)
                .answer("3")
                .points(40.0)
                .questionType(QuestionType.OBJECTIVE)
                .build());
        em.persist(TestQuestion.builder()
                .test(sourceTest)
                .number(2)
                .answer("x=2")
                .points(60.0)
                .questionType(QuestionType.ESSAY)
                .build());
        em.flush();
        TenantContext.set(teacher.getId(), academy.getId(), TeacherAcademyRole.TEACHER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void copies_question_settings_to_a_new_test_without_reusing_entities() throws Exception {
        mockMvc.perform(post("/api/tests/{id}/copy", sourceTest.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("""
                                {"targetClassId": %d, "title": "화목반 중간고사 대비"}
                                """.formatted(targetClass.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("화목반 중간고사 대비"))
                .andExpect(jsonPath("$.classId").value(targetClass.getId()))
                .andExpect(jsonPath("$.questionCount").value(2));

        var copiedTest = testRepository.findAll().stream()
                .filter(test -> "화목반 중간고사 대비".equals(test.getTitle()))
                .findFirst()
                .orElseThrow();
        assertThat(copiedTest.getId()).isNotEqualTo(sourceTest.getId());
        assertThat(copiedTest.getLesson()).isNull();
        assertThat(copiedTest.getSubmissions()).isEmpty();

        var originalQuestions = testQuestionRepository.findByTestIdOrderByNumber(sourceTest.getId());
        var copiedQuestions = testQuestionRepository.findByTestIdOrderByNumber(copiedTest.getId());
        assertThat(copiedQuestions).hasSize(2);
        assertThat(copiedQuestions).extracting(TestQuestion::getNumber)
                .containsExactly(1, 2);
        assertThat(copiedQuestions).extracting(TestQuestion::getAnswer)
                .containsExactly("3", "x=2");
        assertThat(copiedQuestions).extracting(TestQuestion::getPoints)
                .containsExactly(40.0, 60.0);
        assertThat(copiedQuestions).extracting(TestQuestion::getQuestionType)
                .containsExactly(QuestionType.OBJECTIVE, QuestionType.ESSAY);
        assertThat(copiedQuestions.get(0).getId()).isNotEqualTo(originalQuestions.get(0).getId());
    }

    @Test
    void rejects_copy_from_a_class_the_teacher_does_not_manage() throws Exception {
        Teacher otherTeacher = new Teacher();
        otherTeacher.setName("다른 선생님");
        otherTeacher.setUsername("other-copy-teacher");
        otherTeacher.setPin("654321");
        em.persist(otherTeacher);
        AcademyClass otherClass = AcademyClass.builder()
                .name("다른 반")
                .academy(academy)
                .ownerTeacherId(otherTeacher.getId())
                .build();
        em.persist(otherClass);
        com.example.entity.Test otherTest = com.example.entity.Test.builder()
                .title("접근 불가 시험")
                .academy(academy)
                .academyClass(otherClass)
                .build();
        em.persist(otherTest);
        em.flush();

        mockMvc.perform(post("/api/tests/{id}/copy", otherTest.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("""
                                {"targetClassId": %d, "title": "복사 시도"}
                                """.formatted(targetClass.getId())))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }
}
