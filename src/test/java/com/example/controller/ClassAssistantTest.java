package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.ClassAssistant;
import com.example.entity.Student;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.ClassAssistantRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the class assistant (조교) system:
 * - Admin CRUD endpoints under /api/admin/classes/.../assistants and /api/admin/teachers
 * - Role guard on owner reassignment
 * - Role guard on demoting an owner to ASSISTANT
 * - Hibernate ownerFilter scoping for assistants (can see assigned classes only)
 * - Class create/update/delete blocked for assistants
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClassAssistantTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClassAssistantRepository classAssistantRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Academy otherAcademy;
    private Teacher admin;
    private Teacher owner;       // owns classA in `academy`
    private Teacher assistant;   // ASSISTANT role in `academy`
    private Teacher outsider;    // not a member of `academy`
    private AcademyClass classA;
    private AcademyClass classB; // owned by `admin`, no assistant
    private Student studentInA;
    private Student studentInB;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("학원A").build();
        em.persist(academy);
        otherAcademy = Academy.builder().name("학원B").build();
        em.persist(otherAcademy);

        admin = newTeacher("admin", "관리자");
        owner = newTeacher("owner", "담당쌤");
        assistant = newTeacher("ta", "조교쌤");
        outsider = newTeacher("out", "외부쌤");

        em.persist(membership(admin.getId(), academy.getId(), TeacherAcademyRole.ACADEMY_ADMIN));
        em.persist(membership(owner.getId(), academy.getId(), TeacherAcademyRole.TEACHER));
        em.persist(membership(assistant.getId(), academy.getId(), TeacherAcademyRole.ASSISTANT));
        // outsider is intentionally not a member of `academy`
        em.persist(membership(outsider.getId(), otherAcademy.getId(), TeacherAcademyRole.TEACHER));

        classA = AcademyClass.builder().name("반A").academy(academy).ownerTeacherId(owner.getId()).build();
        em.persist(classA);
        classB = AcademyClass.builder().name("반B").academy(academy).ownerTeacherId(admin.getId()).build();
        em.persist(classB);

        studentInA = Student.builder()
                .name("학생A").grade("중3").school("S")
                .academy(academy).academyClass(classA).build();
        em.persist(studentInA);
        studentInB = Student.builder()
                .name("학생B").grade("중3").school("S")
                .academy(academy).academyClass(classB).build();
        em.persist(studentInB);

        em.flush();
    }

    private Teacher newTeacher(String username, String name) {
        Teacher t = new Teacher();
        t.setUsername(username);
        t.setName(name);
        t.setPin("000000");
        em.persist(t);
        return t;
    }

    private TeacherAcademy membership(Long teacherId, Long academyId, TeacherAcademyRole role) {
        return TeacherAcademy.builder()
                .teacherId(teacherId).academyId(academyId).role(role).build();
    }

    private MockHttpSession session(Teacher t, Long academyId, TeacherAcademyRole role) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("userId", t.getId());
        s.setAttribute("userRole", "TEACHER");
        s.setAttribute("activeAcademyId", academyId);
        s.setAttribute("activeRole", role.name());
        return s;
    }

    private MockHttpSession adminSession() { return session(admin, academy.getId(), TeacherAcademyRole.ACADEMY_ADMIN); }
    private MockHttpSession ownerSession() { return session(owner, academy.getId(), TeacherAcademyRole.TEACHER); }
    private MockHttpSession assistantSession() { return session(assistant, academy.getId(), TeacherAcademyRole.ASSISTANT); }

    // ---------------- admin endpoints ----------------

    @Test
    void admin_can_add_and_list_assistant() throws Exception {
        mockMvc.perform(post("/api/admin/classes/{id}/assistants", classA.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"teacherId\":" + assistant.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/classes/assistants").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + classA.getId() + "[0]").value(assistant.getId().intValue()));
    }

    @Test
    void admin_cannot_add_non_member_as_assistant() throws Exception {
        mockMvc.perform(post("/api/admin/classes/{id}/assistants", classA.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"teacherId\":" + outsider.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannot_add_owner_as_their_own_assistant() throws Exception {
        // classA is owned by `owner` — assigning `owner` as classA's assistant must be rejected
        mockMvc.perform(post("/api/admin/classes/{id}/assistants", classA.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"teacherId\":" + owner.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_cannot_add_duplicate_assistant() throws Exception {
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classA.getId()).teacherId(assistant.getId()).build());
        em.flush();

        mockMvc.perform(post("/api/admin/classes/{id}/assistants", classA.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"teacherId\":" + assistant.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_can_remove_assistant() throws Exception {
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classA.getId()).teacherId(assistant.getId()).build());
        em.flush();

        mockMvc.perform(delete("/api/admin/classes/{cid}/assistants/{tid}",
                        classA.getId(), assistant.getId())
                        .session(adminSession()))
                .andExpect(status().isOk());

        assertThat(classAssistantRepository
                .existsByClassIdAndTeacherId(classA.getId(), assistant.getId()))
                .isFalse();
    }

    @Test
    void admin_cannot_set_assistant_as_class_owner() throws Exception {
        mockMvc.perform(patch("/api/admin/classes/{id}/owner", classA.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"teacherId\":" + assistant.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannot_demote_owner_with_classes_to_assistant() throws Exception {
        // `owner` owns classA, so demoting to ASSISTANT must fail
        mockMvc.perform(patch("/api/admin/teachers/{tid}/role", owner.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_demote_teacher_without_classes_to_assistant() throws Exception {
        // Give us a teacher with no classes
        Teacher noClassTeacher = newTeacher("nc", "비담당쌤");
        em.persist(membership(noClassTeacher.getId(), academy.getId(), TeacherAcademyRole.TEACHER));
        em.flush();

        mockMvc.perform(patch("/api/admin/teachers/{tid}/role", noClassTeacher.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void last_admin_cannot_self_demote_to_assistant() throws Exception {
        // Transfer admin's class so the "owns classes" guard doesn't fire,
        // isolating the "last admin" guard.
        classB.setOwnerTeacherId(owner.getId());
        em.flush();

        // Only one admin exists in `academy`. Self-demotion to ASSISTANT
        // would leave the academy admin-less.
        mockMvc.perform(patch("/api/admin/teachers/{tid}/role", admin.getId())
                        .session(adminSession())
                        .contentType("application/json")
                        .content("{\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removing_teacher_cleans_up_assistant_assignments_in_that_academy() throws Exception {
        // assistant is assigned to classA in `academy` and also in another academy
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classA.getId()).teacherId(assistant.getId()).build());

        // Need a class in the *other* academy that assistant is also a TA of
        em.persist(membership(assistant.getId(), otherAcademy.getId(), TeacherAcademyRole.ASSISTANT));
        AcademyClass otherClass = AcademyClass.builder()
                .name("타학원반").academy(otherAcademy).ownerTeacherId(outsider.getId()).build();
        em.persist(otherClass);
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(otherClass.getId()).teacherId(assistant.getId()).build());
        em.flush();

        // Admin removes assistant from `academy` only
        mockMvc.perform(delete("/api/admin/teachers/{tid}", assistant.getId())
                        .session(adminSession()))
                .andExpect(status().isOk());

        // classA assignment is gone, otherClass assignment survives
        assertThat(classAssistantRepository
                .existsByClassIdAndTeacherId(classA.getId(), assistant.getId()))
                .isFalse();
        assertThat(classAssistantRepository
                .existsByClassIdAndTeacherId(otherClass.getId(), assistant.getId()))
                .isTrue();
    }

    // ---------------- assistant data access ----------------

    @Test
    void assistant_can_see_students_of_assigned_class() throws Exception {
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classA.getId()).teacherId(assistant.getId()).build());
        em.flush();

        mockMvc.perform(get("/api/students").session(assistantSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + studentInA.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + studentInB.getId() + ")]").doesNotExist());
    }

    @Test
    void assistant_without_assignment_sees_no_classes() throws Exception {
        // assistant has no class_assistants row yet
        mockMvc.perform(get("/api/classes").session(assistantSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void assistant_cannot_create_class() throws Exception {
        mockMvc.perform(post("/api/classes")
                        .session(assistantSession())
                        .contentType("application/json")
                        .content("{\"name\":\"새반\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_still_sees_their_class_even_when_assistant_added() throws Exception {
        // Sanity check: existing owner behavior is unchanged after the UNION
        classAssistantRepository.save(ClassAssistant.builder()
                .classId(classA.getId()).teacherId(assistant.getId()).build());
        em.flush();

        mockMvc.perform(get("/api/classes").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + classA.getId() + ")]").exists());
    }
}
