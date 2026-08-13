package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentNotification;
import com.example.repository.StudentNotificationRepository;
import com.example.service.StudentNotificationService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentNotificationControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private StudentNotificationService notificationService;
    @Autowired private StudentNotificationRepository notificationRepository;
    @PersistenceContext private EntityManager entityManager;

    private Student student;
    private Student otherStudent;

    @BeforeEach
    void setUp() {
        Academy academy = Academy.builder().name("알림 학원").build();
        entityManager.persist(academy);
        AcademyClass academyClass = AcademyClass.builder()
                .name("알림 반")
                .academy(academy)
                .build();
        entityManager.persist(academyClass);

        student = createStudent("학생A", academy, academyClass);
        otherStudent = createStudent("학생B", academy, academyClass);
        entityManager.flush();
    }

    @Test
    void student_can_list_and_mark_own_notifications_read() throws Exception {
        notificationService.createForStudents(
                List.of(student.getId()), "FEEDBACK", "피드백 도착", "확인해보세요",
                "/student/daily-feedback", "lesson-feedback:1");
        entityManager.flush();

        Long notificationId = notificationRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/api/student-notifications").session(studentSession(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("피드백 도착"))
                .andExpect(jsonPath("$[0].readAt").doesNotExist());

        mockMvc.perform(get("/api/student-notifications/unread-count").session(studentSession(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(patch("/api/student-notifications/{id}/read", notificationId)
                        .session(studentSession(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/student-notifications/unread-count").session(studentSession(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void repeated_source_key_does_not_duplicate_inbox_item() {
        notificationService.createForStudents(
                List.of(student.getId()), "FEEDBACK", "첫 알림", "본문",
                "/student/daily-feedback", "lesson-feedback:2");
        notificationService.createForStudents(
                List.of(student.getId()), "FEEDBACK", "중복 알림", "본문",
                "/student/daily-feedback", "lesson-feedback:2");

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void student_cannot_mark_another_students_notification_read() throws Exception {
        StudentNotification notification = notificationRepository.save(StudentNotification.builder()
                .studentId(otherStudent.getId())
                .type("GENERAL")
                .title("다른 학생 알림")
                .body("본문")
                .build());

        mockMvc.perform(patch("/api/student-notifications/{id}/read", notification.getId())
                        .session(studentSession(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_session_cannot_open_student_inbox() throws Exception {
        MockHttpSession teacherSession = new MockHttpSession();
        teacherSession.setAttribute("userId", 999L);
        teacherSession.setAttribute("userRole", "TEACHER");

        mockMvc.perform(get("/api/student-notifications").session(teacherSession))
                .andExpect(status().isForbidden());
    }

    private Student createStudent(String name, Academy academy, AcademyClass academyClass) {
        Student result = Student.builder()
                .name(name)
                .grade("고2")
                .school("테스트고")
                .academy(academy)
                .academyClass(academyClass)
                .build();
        entityManager.persist(result);
        return result;
    }

    private MockHttpSession studentSession(Student target) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", target.getId());
        session.setAttribute("userRole", "STUDENT");
        return session;
    }
}
