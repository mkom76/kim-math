package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentConsent;
import com.example.entity.StudentStatus;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.StudentConsentRepository;
import com.example.repository.StudentRepository;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for bulk student registration + parent consent flow:
 *
 * <ul>
 *   <li>Admin bulk-creates students with status=PENDING_CONSENT, PIN auto-derived
 *       from parent phone last 4.</li>
 *   <li>Public {@code GET /api/consents/{token}} returns masked info.</li>
 *   <li>{@code POST /api/consents/{token}/agree} requires the matching last4 and
 *       moves status → ACTIVE.</li>
 *   <li>Re-using the token after consent is rejected.</li>
 *   <li>Expired token is rejected.</li>
 *   <li>Student login is blocked while PENDING_CONSENT, unblocked after consent.</li>
 *   <li>ASSISTANT role cannot bulk-create.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentBulkConsentTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentConsentRepository studentConsentRepository;
    @Autowired private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass clazz;
    private Teacher admin;
    private Teacher assistant;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);

        admin = new Teacher();
        admin.setUsername("admin"); admin.setName("관리자"); admin.setPin("000000");
        em.persist(admin);
        em.persist(TeacherAcademy.builder()
                .teacherId(admin.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.ACADEMY_ADMIN).build());

        assistant = new Teacher();
        assistant.setUsername("ta"); assistant.setName("조교"); assistant.setPin("000000");
        em.persist(assistant);
        em.persist(TeacherAcademy.builder()
                .teacherId(assistant.getId()).academyId(academy.getId())
                .role(TeacherAcademyRole.ASSISTANT).build());

        clazz = AcademyClass.builder()
                .name("고2 A반").academy(academy).ownerTeacherId(admin.getId()).build();
        em.persist(clazz);
        em.flush();
    }

    private MockHttpSession session(Teacher t, TeacherAcademyRole role) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("userId", t.getId());
        s.setAttribute("userRole", "TEACHER");
        s.setAttribute("activeAcademyId", academy.getId());
        s.setAttribute("activeRole", role.name());
        return s;
    }

    private String bulkCreateOne(String name, String parentPhone) throws Exception {
        String body = """
                {
                  "classId": %d,
                  "students": [
                    {
                      "name": "%s",
                      "grade": "고2",
                      "school": "A고",
                      "parentName": "%s",
                      "parentPhone": "%s"
                    }
                  ]
                }
                """.formatted(clazz.getId(), name, name + "부모", parentPhone);

        MvcResult res = mockMvc.perform(post("/api/students/bulk")
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.get("created").get(0).get("consentToken").asText();
    }

    // ---------------- bulk create ----------------

    @Test
    void admin_bulk_creates_with_pending_consent_and_pin_from_phone() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();
        em.clear();

        Student created = studentRepository.findAll().stream()
                .filter(s -> "김철수".equals(s.getName())).findFirst().orElseThrow();
        assertThat(created.getStatus()).isEqualTo(StudentStatus.PENDING_CONSENT);
        assertThat(created.getPin()).isNull();
        assertThat(created.getPinHash()).isNotBlank();
        assertThat(created.getParentPhone()).isEqualTo("010-1234-5678");

        assertThat(studentConsentRepository.findByToken(token)).isPresent();
    }

    @Test
    void assistant_cannot_bulk_create() throws Exception {
        String body = """
                {
                  "classId": %d,
                  "students": [
                    {"name":"x","grade":"고2","school":"A","parentName":"p","parentPhone":"01011112222"}
                  ]
                }
                """.formatted(clazz.getId());
        mockMvc.perform(post("/api/students/bulk")
                        .session(session(assistant, TeacherAcademyRole.ASSISTANT))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulk_create_rejects_bad_phone_format() throws Exception {
        String body = """
                {
                  "classId": %d,
                  "students": [
                    {"name":"x","grade":"고2","school":"A","parentName":"p","parentPhone":"abc"}
                  ]
                }
                """.formatted(clazz.getId());
        mockMvc.perform(post("/api/students/bulk")
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------------- consent endpoints (public) ----------------

    @Test
    void public_get_returns_masked_info() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();

        mockMvc.perform(get("/api/consents/{t}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("김철수"))
                .andExpect(jsonPath("$.parentPhoneMasked").value("010-****-5678"))
                .andExpect(jsonPath("$.alreadyConsented").value(false))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void agree_with_matching_last4_activates_student() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();

        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isOk());

        // Service mutated managed entities but did not flush; push to DB then re-read.
        em.flush();
        em.clear();
        Student s = studentRepository.findAll().stream()
                .filter(st -> "김철수".equals(st.getName())).findFirst().orElseThrow();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.ACTIVE);

        // Token must be invalidated after consent
        assertThat(studentConsentRepository.findByToken(token)).isEmpty();
    }

    @Test
    void agree_with_wrong_last4_is_rejected() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();

        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"0000\"}"))
                .andExpect(status().isForbidden());

        Student s = studentRepository.findAll().stream()
                .filter(st -> "김철수".equals(st.getName())).findFirst().orElseThrow();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PENDING_CONSENT);
    }

    @Test
    void agreeing_twice_is_rejected() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();

        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isOk());
        em.flush();

        // Token is nulled, so the second call now resolves nothing → 403
        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expired_token_is_rejected() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");

        // Force the token expiry into the past
        StudentConsent consent = studentConsentRepository.findByToken(token).orElseThrow();
        consent.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        studentConsentRepository.save(consent);
        em.flush();

        mockMvc.perform(get("/api/consents/{t}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true));

        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalid_token_get_returns_403() throws Exception {
        mockMvc.perform(get("/api/consents/{t}", "nonsense"))
                .andExpect(status().isForbidden());
    }

    // ---------------- login gate ----------------

    @Test
    void student_login_blocked_while_pending_consent() throws Exception {
        bulkCreateOne("김철수", "010-1234-5678");
        em.flush();
        Long sid = studentRepository.findAll().stream()
                .filter(st -> "김철수".equals(st.getName())).findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/auth/student/login")
                        .contentType("application/json")
                        .content("{\"studentId\":%d,\"pin\":\"5678\"}".formatted(sid)))
                .andExpect(status().isForbidden());
    }

    @Test
    void student_login_succeeds_after_consent() throws Exception {
        String token = bulkCreateOne("김철수", "010-1234-5678");
        em.flush();
        Long sid = studentRepository.findAll().stream()
                .filter(st -> "김철수".equals(st.getName())).findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/consents/{t}/agree", token)
                        .contentType("application/json")
                        .content("{\"parentPhoneLast4\":\"5678\"}"))
                .andExpect(status().isOk());
        em.flush();

        mockMvc.perform(post("/api/auth/student/login")
                        .contentType("application/json")
                        .content("{\"studentId\":%d,\"pin\":\"5678\"}".formatted(sid)))
                .andExpect(status().isOk());
    }
}
