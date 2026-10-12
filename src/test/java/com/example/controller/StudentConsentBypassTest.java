package com.example.controller;

import com.example.entity.*;
import com.example.repository.StudentConsentRepository;
import com.example.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentConsentBypassTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentConsentRepository consentRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass clazz;
    private MockHttpSession teacherSession;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("동의 생략 학원").build();
        em.persist(academy);
        Teacher teacher = Teacher.builder().username("consent-admin").name("관리자").pin("000000").build();
        em.persist(teacher);
        em.persist(TeacherAcademy.builder().teacherId(teacher.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.ACADEMY_ADMIN).build());
        clazz = AcademyClass.builder().name("기본반").academy(academy).ownerTeacherId(teacher.getId()).build();
        em.persist(clazz);
        teacherSession = new MockHttpSession();
        teacherSession.setAttribute("userId", teacher.getId());
        teacherSession.setAttribute("userRole", "TEACHER");
        teacherSession.setAttribute("activeAcademyId", academy.getId());
        teacherSession.setAttribute("activeRole", "ACADEMY_ADMIN");
        em.flush();
    }

    @Test
    void single_registration_allows_immediate_login_without_consent_evidence() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/students").session(teacherSession)
                        .contentType("application/json").content("""
                                {"name":"신규학생","grade":"고1","school":"A고","classId":%d,
                                 "parentName":"보호자","parentPhone":"010-1111-4321"}
                                """.formatted(clazz.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.consentToken").isEmpty())
                .andReturn();
        long studentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        em.flush();
        em.clear();
        Student student = studentRepository.findById(studentId).orElseThrow();
        assertThat(student.getPin()).isNull();
        assertThat(student.getPinHash()).isNotBlank();
        assertThat(consentRepository.findByStudentIdOrderByIdDesc(studentId)).isEmpty();
        login(studentId, "4321").andExpect(status().isOk());
    }

    @Test
    void bulk_registration_activates_every_student_without_issuing_links() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/students/bulk").session(teacherSession)
                        .contentType("application/json").content("""
                                {"classId":%d,"students":[
                                  {"name":"학생하나","grade":"고1","school":"A고","parentName":"보호자1","parentPhone":"010-1111-1234"},
                                  {"name":"학생둘","grade":"고2","school":"B고","parentName":"보호자2","parentPhone":"010-2222-5678"}
                                ]}
                                """.formatted(clazz.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()").value(2))
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString()).get("created");
        for (int i = 0; i < created.size(); i++) {
            JsonNode item = created.get(i);
            long studentId = item.get("studentId").asLong();
            assertThat(item.get("consentToken").isNull()).isTrue();
            assertThat(studentRepository.findById(studentId).orElseThrow().getStatus()).isEqualTo(StudentStatus.ACTIVE);
            assertThat(consentRepository.findByStudentIdOrderByIdDesc(studentId)).isEmpty();
            login(studentId, i == 0 ? "1234" : "5678").andExpect(status().isOk());
        }
    }

    @Test
    void pending_student_activates_on_valid_pin_and_remember_me_works_without_marking_consent() throws Exception {
        Student student = existingStudent(StudentStatus.PENDING_CONSENT);
        consentRepository.save(StudentConsent.builder().studentId(student.getId()).consentVersion("v1")
                .token("existing-parent-link").tokenIssuedAt(LocalDateTime.now())
                .tokenExpiresAt(LocalDateTime.now().plusDays(1)).build());
        em.flush();
        MvcResult result = mockMvc.perform(post("/api/auth/student/login")
                        .contentType("application/json").content("""
                                {"studentId":%d,"pin":"5678","rememberMe":true}
                                """.formatted(student.getId())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("KM_REMEMBER"))
                .andReturn();
        em.flush();
        em.clear();
        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.ACTIVE);
        StudentConsent consent = consentRepository.findByToken("existing-parent-link").orElseThrow();
        assertThat(consent.getConsentedAt()).isNull();
        assertThat(consent.getSignerType()).isNull();
        mockMvc.perform(get("/api/auth/me").cookie(result.getResponse().getCookie("KM_REMEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getId()));
        // A deferred, real parent consent can still be recorded with the original link.
        mockMvc.perform(post("/api/consents/existing-parent-link/agree")
                        .contentType("application/json").content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(consentRepository.findByStudentIdOrderByIdDesc(student.getId()).getFirst().getConsentedAt()).isNotNull();
    }

    @Test
    void incorrect_pin_does_not_activate_pending_student() throws Exception {
        Student student = existingStudent(StudentStatus.PENDING_CONSENT);
        login(student.getId(), "0000").andExpect(status().isUnauthorized());
        em.flush();
        em.clear();
        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.PENDING_CONSENT);
    }

    @Test
    void locked_pending_student_is_not_activated() throws Exception {
        Student student = existingStudent(StudentStatus.PENDING_CONSENT);
        student.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        login(student.getId(), "5678").andExpect(status().isTooManyRequests());
        assertThat(student.getStatus()).isEqualTo(StudentStatus.PENDING_CONSENT);
    }

    @Test
    void revoked_student_remains_blocked() throws Exception {
        Student student = existingStudent(StudentStatus.REVOKED);
        login(student.getId(), "5678").andExpect(status().isForbidden());
        assertThat(student.getStatus()).isEqualTo(StudentStatus.REVOKED);
    }

    private Student existingStudent(StudentStatus status) {
        Student student = studentRepository.save(Student.builder().name("기존학생").grade("고1").school("A고")
                .pin("5678").parentName("보호자").parentPhone("010-1234-5678")
                .status(status).academy(academy).academyClass(clazz).build());
        em.flush();
        return student;
    }

    private org.springframework.test.web.servlet.ResultActions login(long studentId, String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/student/login").contentType("application/json")
                .content("{\"studentId\":%d,\"pin\":\"%s\"}".formatted(studentId, pin)));
    }
}
