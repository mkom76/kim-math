package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Clinic;
import com.example.entity.ClinicRegistration;
import com.example.entity.ClinicRegistrationStatus;
import com.example.entity.ClinicStatus;
import com.example.entity.Student;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.ClinicRegistrationRepository;
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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicAssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClinicRegistrationRepository clinicRegistrationRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Teacher teacher;
    private AcademyClass clinicClass;
    private Clinic clinic;
    private Student student;
    private Student classmate;
    private Student otherClassStudent;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);

        teacher = new Teacher();
        teacher.setUsername("clinic-teacher");
        teacher.setName("클리닉 선생님");
        teacher.setPin("000000");
        em.persist(teacher);
        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.TEACHER)
                .build());

        clinicClass = AcademyClass.builder()
                .name("클리닉반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        em.persist(clinicClass);
        AcademyClass otherClass = AcademyClass.builder()
                .name("다른반")
                .academy(academy)
                .ownerTeacherId(teacher.getId())
                .build();
        em.persist(otherClass);

        student = newStudent("학생A", clinicClass);
        classmate = newStudent("학생B", clinicClass);
        otherClassStudent = newStudent("학생C", otherClass);

        clinic = Clinic.builder()
                .academyClass(clinicClass)
                .clinicDate(LocalDate.now().plusDays(1))
                .clinicTime(LocalTime.of(14, 0))
                .status(ClinicStatus.OPEN)
                .build();
        em.persist(clinic);
        em.flush();
    }

    @Test
    void teacher_can_assign_and_cancel_student_in_clinic_class() throws Exception {
        mockMvc.perform(post("/api/clinics/{clinicId}/assign", clinic.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"studentId\":" + student.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(student.getId().intValue()))
                .andExpect(jsonPath("$.status").value("REGISTERED"));

        ClinicRegistration registration = clinicRegistrationRepository
                .findByClinicIdAndStudentId(clinic.getId(), student.getId())
                .orElseThrow();
        assertThat(registration.getStatus()).isEqualTo(ClinicRegistrationStatus.REGISTERED);

        mockMvc.perform(delete("/api/clinics/{clinicId}/assign/{studentId}",
                        clinic.getId(), student.getId())
                        .session(teacherSession()))
                .andExpect(status().isNoContent());

        assertThat(registration.getStatus()).isEqualTo(ClinicRegistrationStatus.CANCELLED);
    }

    @Test
    void teacher_cannot_assign_student_from_different_class() throws Exception {
        mockMvc.perform(post("/api/clinics/{clinicId}/assign", clinic.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"studentId\":" + otherClassStudent.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해당 클리닉과 같은 반의 학생만 배정할 수 있습니다"));

        assertThat(clinicRegistrationRepository
                .findByClinicIdAndStudentId(clinic.getId(), otherClassStudent.getId()))
                .isEmpty();
    }

    @Test
    void student_can_register_self_but_not_a_classmate() throws Exception {
        mockMvc.perform(post("/api/clinics/{clinicId}/register", clinic.getId())
                        .session(studentSession(student))
                        .contentType("application/json")
                        .content("{\"studentId\":" + student.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/clinics/{clinicId}/register", clinic.getId())
                        .session(studentSession(student))
                        .contentType("application/json")
                        .content("{\"studentId\":" + classmate.getId() + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 클리닉 신청만 변경할 수 있습니다"));
    }

    @Test
    void student_and_teacher_endpoints_are_role_separated() throws Exception {
        mockMvc.perform(post("/api/clinics/{clinicId}/assign", clinic.getId())
                        .session(studentSession(student))
                        .contentType("application/json")
                        .content("{\"studentId\":" + student.getId() + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/clinics/{clinicId}/register", clinic.getId())
                        .session(teacherSession())
                        .contentType("application/json")
                        .content("{\"studentId\":" + student.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void student_cannot_cancel_classmates_registration() throws Exception {
        em.persist(ClinicRegistration.builder()
                .clinic(clinic)
                .student(classmate)
                .status(ClinicRegistrationStatus.REGISTERED)
                .build());
        em.flush();

        mockMvc.perform(delete("/api/clinics/{clinicId}/register/{studentId}",
                        clinic.getId(), classmate.getId())
                        .session(studentSession(student)))
                .andExpect(status().isForbidden());
    }

    private Student newStudent(String name, AcademyClass academyClass) {
        Student value = Student.builder()
                .name(name)
                .grade("중3")
                .school("학교")
                .academy(academy)
                .academyClass(academyClass)
                .build();
        em.persist(value);
        return value;
    }

    private MockHttpSession teacherSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", TeacherAcademyRole.TEACHER.name());
        return session;
    }

    private MockHttpSession studentSession(Student value) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", value.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        return session;
    }
}
