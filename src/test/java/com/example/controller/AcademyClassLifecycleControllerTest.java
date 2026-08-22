package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademyClassLifecycleControllerTest {

    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Teacher teacher;
    private AcademyClass activeClass;
    private AcademyClass endedClass;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("종강 학원").build();
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("담당 선생님");
        teacher.setUsername("class-lifecycle-teacher");
        teacher.setPin("123456");
        em.persist(teacher);
        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER)
                .build());

        activeClass = AcademyClass.builder()
                .name("운영반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        endedClass = AcademyClass.builder()
                .name("종강반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .endedAt(LocalDateTime.now().minusDays(1))
                .build();
        em.persist(activeClass);
        em.persist(endedClass);
        em.flush();
    }

    @Test
    void class_list_excludes_ended_classes_by_default_and_can_include_history() throws Exception {
        mockMvc.perform(get("/api/classes").session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("운영반"));

        mockMvc.perform(get("/api/classes")
                        .param("includeEnded", "true")
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("운영반", "종강반")));
    }

    @Test
    void teacher_can_end_and_reopen_a_class_without_deleting_it() throws Exception {
        mockMvc.perform(patch("/api/classes/{id}/end", activeClass.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());

        mockMvc.perform(patch("/api/classes/{id}/reopen", activeClass.getId())
                        .session(teacherSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(false))
                .andExpect(jsonPath("$.endedAt").doesNotExist());
    }

    @Test
    void new_tests_cannot_be_created_in_an_ended_class() throws Exception {
        mockMvc.perform(post("/api/tests")
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("""
                                {"title":"종강 뒤 시험","academyId":%d,"classId":%d}
                                """.formatted(academy.getId(), endedClass.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("종강한 반에는 새 항목을 등록할 수 없습니다. 먼저 반 운영을 재개해주세요."));
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
