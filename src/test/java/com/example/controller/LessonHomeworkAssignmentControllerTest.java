package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.Lesson;
import com.example.entity.Student;
import com.example.entity.StudentHomework;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.StudentHomeworkRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LessonHomeworkAssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentHomeworkRepository studentHomeworkRepository;
    @PersistenceContext private EntityManager em;

    private Teacher teacher;
    private Academy academy;
    private Lesson lesson;
    private Student studentA;
    private Student studentB;
    private Homework homeworkA;
    private Homework homeworkB;

    @BeforeEach
    void setUp() {
        academy = new Academy();
        academy.setName("학원A");
        em.persist(academy);

        teacher = new Teacher();
        teacher.setName("선생A");
        teacher.setUsername("lesson-assignment-teacher");
        teacher.setPin("000001");
        em.persist(teacher);

        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER)
                .build());

        AcademyClass clazz = AcademyClass.builder()
                .name("반1")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        em.persist(clazz);

        lesson = Lesson.builder()
                .academy(academy)
                .academyClass(clazz)
                .lessonDate(LocalDate.of(2026, 6, 14))
                .build();
        em.persist(lesson);

        studentA = Student.builder()
                .name("학생A")
                .grade("고1")
                .school("학교A")
                .academy(academy)
                .academyClass(clazz)
                .pin("1111")
                .build();
        em.persist(studentA);

        studentB = Student.builder()
                .name("학생B")
                .grade("고1")
                .school("학교A")
                .academy(academy)
                .academyClass(clazz)
                .pin("2222")
                .build();
        em.persist(studentB);

        homeworkA = Homework.builder()
                .title("숙제A")
                .questionCount(10)
                .academy(academy)
                .academyClass(clazz)
                .lesson(lesson)
                .build();
        em.persist(homeworkA);

        homeworkB = Homework.builder()
                .title("숙제B")
                .questionCount(10)
                .academy(academy)
                .academyClass(clazz)
                .lesson(lesson)
                .build();
        em.persist(homeworkB);

        em.persist(StudentHomework.builder()
                .student(studentA)
                .homework(homeworkA)
                .build());
        em.persist(StudentHomework.builder()
                .student(studentB)
                .homework(homeworkA)
                .build());

        em.flush();
        em.clear();
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
    void assignHomeworks_acceptsNullToRemoveUnsubmittedAssignment() throws Exception {
        String body = "{" +
                "\"" + studentA.getId() + "\":null," +
                "\"" + studentB.getId() + "\":" + homeworkB.getId() +
                "}";

        mockMvc.perform(post("/api/lessons/{lessonId}/assign-homeworks", lesson.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        assertThat(studentHomeworkRepository.findByStudentIdAndHomeworkId(studentA.getId(), homeworkA.getId()))
                .isEmpty();
        assertThat(studentHomeworkRepository.findByStudentId(studentA.getId()))
                .noneMatch(sh -> sh.getHomework().getId().equals(homeworkB.getId()));

        assertThat(studentHomeworkRepository.findByStudentIdAndHomeworkId(studentB.getId(), homeworkA.getId()))
                .isEmpty();
        assertThat(studentHomeworkRepository.findByStudentIdAndHomeworkId(studentB.getId(), homeworkB.getId()))
                .isPresent();
    }
}
