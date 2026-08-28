package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Lesson;
import com.example.entity.Homework;
import com.example.entity.StudentHomework;
import com.example.entity.StudentSubmission;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentClassSwitchControllerTest {

    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    private Student student;
    private Academy academy;
    private AcademyClass firstClass;
    private AcademyClass secondClass;
    private AcademyClass completedClass;
    private com.example.entity.Test firstTest;
    private com.example.entity.Test secondTest;
    private Homework firstHomework;
    private Homework secondHomework;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("다중 소속 학원").build();
        em.persist(academy);

        firstClass = persistClass("고1 A반", null);
        secondClass = persistClass("고1 심화반", null);
        completedClass = persistClass("중3 종강반", LocalDateTime.now().minusMonths(1));

        student = Student.builder()
                .name("다중반 학생")
                .grade("고1")
                .school("테스트고")
                .academy(academy)
                .academyClass(firstClass)
                .build();
        em.persist(student);

        persistEnrollment(firstClass, StudentClassEnrollmentStatus.ACTIVE, null);
        persistEnrollment(secondClass, StudentClassEnrollmentStatus.ACTIVE, null);
        persistEnrollment(
                completedClass,
                StudentClassEnrollmentStatus.COMPLETED,
                completedClass.getEndedAt());

        em.persist(Lesson.builder()
                .academy(academy)
                .academyClass(firstClass)
                .lessonDate(LocalDate.of(2026, 8, 1))
                .build());
        em.persist(Lesson.builder()
                .academy(academy)
                .academyClass(secondClass)
                .lessonDate(LocalDate.of(2026, 8, 2))
                .build());

        firstTest = persistTest("A반 시험", firstClass);
        secondTest = persistTest("심화반 시험", secondClass);
        em.persist(StudentSubmission.builder()
                .student(student)
                .test(firstTest)
                .totalScore(80)
                .submittedAt(LocalDateTime.now())
                .build());
        em.persist(StudentSubmission.builder()
                .student(student)
                .test(secondTest)
                .totalScore(90)
                .submittedAt(LocalDateTime.now())
                .build());

        firstHomework = persistHomework("A반 숙제", firstClass);
        secondHomework = persistHomework("심화반 숙제", secondClass);
        em.persist(StudentHomework.builder()
                .student(student)
                .homework(firstHomework)
                .incorrectCount(2)
                .build());
        em.persist(StudentHomework.builder()
                .student(student)
                .homework(secondHomework)
                .incorrectCount(1)
                .build());
        em.flush();
    }

    @Test
    void student_switches_active_class_and_lesson_scope_changes_with_session() throws Exception {
        MockHttpSession session = studentSession(firstClass.getId());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentClasses", hasSize(3)))
                .andExpect(jsonPath("$.activeStudentClassId").value(firstClass.getId()))
                .andExpect(jsonPath("$.studentClassReadOnly").value(false));

        mockMvc.perform(get("/api/lessons/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].classId").value(firstClass.getId()));

        mockMvc.perform(get("/api/submissions/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].testId").value(firstTest.getId()));

        mockMvc.perform(get("/api/student-homeworks/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].homeworkId").value(firstHomework.getId()));

        mockMvc.perform(post("/api/auth/switch-class")
                        .with(csrf())
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {"classId":%d}
                                """.formatted(secondClass.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudentClassId").value(secondClass.getId()));

        mockMvc.perform(get("/api/lessons/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].classId").value(secondClass.getId()));

        mockMvc.perform(get("/api/submissions/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].testId").value(secondTest.getId()));

        mockMvc.perform(get("/api/student-homeworks/student/{studentId}", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].homeworkId").value(secondHomework.getId()));
    }

    @Test
    void completed_class_is_not_selectable_while_an_active_class_exists() throws Exception {
        mockMvc.perform(post("/api/auth/switch-class")
                        .with(csrf())
                        .session(studentSession(firstClass.getId()))
                        .contentType("application/json")
                        .content("""
                                {"classId":%d}
                                """.formatted(completedClass.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("선택할 수 없는 반입니다"));
    }

    @Test
    void completed_only_student_gets_read_only_history_and_cannot_submit() throws Exception {
        Student completedStudent = Student.builder()
                .name("종강 이력 학생")
                .grade("고1")
                .school("테스트고")
                .academy(academy)
                .academyClass(completedClass)
                .build();
        em.persist(completedStudent);
        em.persist(StudentClassEnrollment.builder()
                .student(completedStudent)
                .academyClass(completedClass)
                .status(StudentClassEnrollmentStatus.COMPLETED)
                .startedAt(LocalDateTime.now().minusMonths(3))
                .endedAt(completedClass.getEndedAt())
                .build());
        com.example.entity.Test completedTest = persistTest("종강반 시험", completedClass);
        em.flush();

        MockHttpSession session = studentSession(completedStudent, null);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudentClassId").value(completedClass.getId()))
                .andExpect(jsonPath("$.studentClassReadOnly").value(true));

        mockMvc.perform(post("/api/submissions/me/test/{testId}", completedTest.getId())
                        .with(csrf())
                        .session(session)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("종강한 반은 조회만 할 수 있습니다"));
    }

    private AcademyClass persistClass(String name, LocalDateTime endedAt) {
        AcademyClass academyClass = AcademyClass.builder()
                .name(name)
                .academy(academy)
                .endedAt(endedAt)
                .build();
        em.persist(academyClass);
        return academyClass;
    }

    private void persistEnrollment(
            AcademyClass academyClass,
            StudentClassEnrollmentStatus status,
            LocalDateTime endedAt) {
        em.persist(StudentClassEnrollment.builder()
                .student(student)
                .academyClass(academyClass)
                .status(status)
                .startedAt(LocalDateTime.now().minusMonths(2))
                .endedAt(endedAt)
                .build());
    }

    private com.example.entity.Test persistTest(String title, AcademyClass academyClass) {
        com.example.entity.Test test = com.example.entity.Test.builder()
                .title(title)
                .academy(academy)
                .academyClass(academyClass)
                .build();
        em.persist(test);
        return test;
    }

    private Homework persistHomework(String title, AcademyClass academyClass) {
        Homework homework = Homework.builder()
                .title(title)
                .questionCount(10)
                .academy(academy)
                .academyClass(academyClass)
                .build();
        em.persist(homework);
        return homework;
    }

    private MockHttpSession studentSession(Long activeClassId) {
        return studentSession(student, activeClassId);
    }

    private MockHttpSession studentSession(Student targetStudent, Long activeClassId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", targetStudent.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("userName", targetStudent.getName());
        session.setAttribute("studentAcademyId", academy.getId());
        if (activeClassId != null) {
            session.setAttribute("activeStudentClassId", activeClassId);
        }
        return session;
    }
}
