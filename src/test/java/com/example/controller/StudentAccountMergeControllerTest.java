package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Homework;
import com.example.entity.RememberMeToken;
import com.example.entity.Student;
import com.example.entity.StudentAccountMerge;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.entity.StudentHomework;
import com.example.entity.StudentStatus;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.StudentAccountMergeRepository;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentHomeworkRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
class StudentAccountMergeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentClassEnrollmentRepository enrollmentRepository;
    @Autowired private StudentHomeworkRepository homeworkRepository;
    @Autowired private StudentAccountMergeRepository mergeRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private AcademyClass firstClass;
    private AcademyClass secondClass;
    private Teacher admin;
    private Teacher teacher;
    private Student target;
    private Student source;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("계정 통합 학원").build();
        em.persist(academy);

        admin = persistTeacher("merge-admin", TeacherAcademyRole.ACADEMY_ADMIN);
        teacher = persistTeacher("merge-teacher", TeacherAcademyRole.TEACHER);
        firstClass = persistClass("기존 반", admin);
        secondClass = persistClass("새 반", teacher);
        target = persistStudent("김통합", firstClass, "고1", "통합고", "010-1111-2222");
        source = persistStudent("김통합", secondClass, "고1", "통합고", "01011112222");
        persistEnrollment(target, firstClass);
        persistEnrollment(source, secondClass);
        em.flush();
        em.clear();
    }

    @Test
    void admin_previews_and_merges_distinct_class_history_atomically() throws Exception {
        Homework homework = Homework.builder()
                .title("중복 계정 숙제")
                .questionCount(20)
                .academy(academy)
                .academyClass(secondClass)
                .build();
        em.persist(homework);
        em.persist(StudentHomework.builder()
                .student(em.getReference(Student.class, source.getId()))
                .homework(homework)
                .incorrectCount(3)
                .build());
        em.persist(RememberMeToken.builder()
                .selectorHash("s".repeat(64))
                .validatorHash("v".repeat(64))
                .userRole("STUDENT")
                .userId(source.getId())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/admin/student-account-merges/candidates")
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].students", hasSize(2)))
                .andExpect(jsonPath("$[0].matchReasons", hasSize(2)));

        String request = requestBody(target.getId(), source.getId());
        mockMvc.perform(post("/api/admin/student-account-merges/preview")
                        .with(csrf())
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergeable").value(true))
                .andExpect(jsonPath("$.target.id").value(target.getId()))
                .andExpect(jsonPath("$.sources[0].id").value(source.getId()))
                .andExpect(jsonPath("$.impacts[?(@.key == 'homeworks')].count").value(1))
                .andExpect(jsonPath("$.impacts[?(@.key == 'rememberMeTokens')].count").value(1));

        mockMvc.perform(post("/api/admin/student-account-merges")
                        .with(csrf())
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetStudentId").value(target.getId()))
                .andExpect(jsonPath("$.mergedSourceStudentIds[0]").value(source.getId()));

        em.flush();
        em.clear();
        assertThat(studentRepository.findById(source.getId())).isEmpty();
        assertThat(studentRepository.findById(target.getId())).isPresent();
        assertThat(enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(target.getId()))
                .hasSize(2);
        assertThat(homeworkRepository.findByStudentId(target.getId())).hasSize(1);
        assertThat(homeworkRepository.findByStudentId(source.getId())).isEmpty();
        assertThat(em.createQuery("select count(t) from RememberMeToken t where t.userRole = 'STUDENT' " +
                        "and t.userId = :studentId", Long.class)
                .setParameter("studentId", source.getId())
                .getSingleResult()).isZero();
        assertThat(mergeRepository.findAll())
                .singleElement()
                .extracting(StudentAccountMerge::getSourceStudentId,
                        StudentAccountMerge::getTargetStudentId,
                        StudentAccountMerge::getMergedByTeacherId)
                .containsExactly(source.getId(), target.getId(), admin.getId());
    }

    @Test
    void overlapping_records_block_execution_without_changing_either_account() throws Exception {
        persistEnrollment(source, firstClass);
        em.flush();
        em.clear();
        String request = requestBody(target.getId(), source.getId());

        mockMvc.perform(post("/api/admin/student-account-merges/preview")
                        .with(csrf())
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergeable").value(false))
                .andExpect(jsonPath("$.conflicts[0].key").value("enrollments"))
                .andExpect(jsonPath("$.conflicts[0].conflictCount").value(1));

        mockMvc.perform(post("/api/admin/student-account-merges")
                        .with(csrf())
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(studentRepository.findById(source.getId())).isPresent();
        assertThat(mergeRepository.findAll()).isEmpty();
    }

    @Test
    void normal_teacher_cannot_list_or_merge_accounts() throws Exception {
        MockHttpSession teacherSession = session(teacher, TeacherAcademyRole.TEACHER);
        mockMvc.perform(get("/api/admin/student-account-merges/candidates").session(teacherSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/student-account-merges")
                        .with(csrf())
                        .session(teacherSession)
                        .contentType("application/json")
                        .content(requestBody(target.getId(), source.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void name_alone_does_not_create_a_candidate() throws Exception {
        persistStudent("김통합", firstClass, "고2", "다른고", "010-9999-8888");
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/admin/student-account-merges/candidates")
                        .session(session(admin, TeacherAcademyRole.ACADEMY_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].students", hasSize(2)));
    }

    private Teacher persistTeacher(String username, TeacherAcademyRole role) {
        Teacher value = new Teacher();
        value.setUsername(username);
        value.setName(username);
        value.setPin("123456");
        em.persist(value);
        em.persist(TeacherAcademy.builder()
                .teacherId(value.getId())
                .academyId(academy.getId())
                .role(role)
                .build());
        return value;
    }

    private AcademyClass persistClass(String name, Teacher owner) {
        AcademyClass value = AcademyClass.builder()
                .name(name)
                .academy(academy)
                .ownerTeacherId(owner.getId())
                .build();
        em.persist(value);
        return value;
    }

    private Student persistStudent(
            String name,
            AcademyClass academyClass,
            String grade,
            String school,
            String parentPhone) {
        Student value = Student.builder()
                .name(name)
                .grade(grade)
                .school(school)
                .parentName("보호자")
                .parentPhone(parentPhone)
                .status(StudentStatus.ACTIVE)
                .academy(academy)
                .academyClass(academyClass)
                .build();
        em.persist(value);
        return value;
    }

    private void persistEnrollment(Student student, AcademyClass academyClass) {
        em.persist(StudentClassEnrollment.builder()
                .student(em.getReference(Student.class, student.getId()))
                .academyClass(em.getReference(AcademyClass.class, academyClass.getId()))
                .status(StudentClassEnrollmentStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .build());
    }

    private MockHttpSession session(Teacher user, TeacherAcademyRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", role.name());
        return session;
    }

    private static String requestBody(Long targetId, Long sourceId) {
        return """
                {
                  "targetStudentId": %d,
                  "sourceStudentIds": [%d]
                }
                """.formatted(targetId, sourceId);
    }
}
