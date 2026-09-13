package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.ClassAssistant;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.entity.StudentHomework;
import com.example.entity.StudentSubmission;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.StudentClassEnrollmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentEnrollmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentClassEnrollmentRepository enrollmentRepository;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Academy otherAcademy;
    private Teacher owner;
    private Teacher otherOwner;
    private Teacher admin;
    private Teacher assistant;
    private AcademyClass sourceClass;
    private AcademyClass targetClass;
    private AcademyClass otherOwnerClass;
    private AcademyClass otherAcademyClass;
    private Student student;
    private StudentClassEnrollment originalEnrollment;

    @BeforeEach
    void setUp() {
        academy = Academy.builder().name("수강 관리 학원").build();
        otherAcademy = Academy.builder().name("다른 학원").build();
        em.persist(academy);
        em.persist(otherAcademy);

        owner = persistTeacher("enrollment-owner", TeacherAcademyRole.TEACHER);
        otherOwner = persistTeacher("enrollment-other-owner", TeacherAcademyRole.TEACHER);
        admin = persistTeacher("enrollment-admin", TeacherAcademyRole.ACADEMY_ADMIN);
        assistant = persistTeacher("enrollment-assistant", TeacherAcademyRole.ASSISTANT);

        sourceClass = persistClass("고1 정규반", academy, owner);
        targetClass = persistClass("고1 심화반", academy, owner);
        otherOwnerClass = persistClass("다른 선생님 반", academy, otherOwner);
        otherAcademyClass = persistClass("다른 학원 반", otherAcademy, owner);
        em.persist(ClassAssistant.builder()
                .classId(sourceClass.getId()).teacherId(assistant.getId()).build());

        student = persistStudent("다중 반 학생", sourceClass);
        originalEnrollment = persistEnrollment(student, sourceClass);
        flushAndClear();
    }

    @Test
    void adding_class_preserves_original_membership() throws Exception {
        mockMvc.perform(add(student, targetClass).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].classId", containsInAnyOrder(
                        sourceClass.getId().intValue(), targetClass.getId().intValue())))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("ACTIVE", "ACTIVE")))
                .andExpect(jsonPath("$[*].canManage", containsInAnyOrder(true, true)));

        flushAndClear();
        assertThat(enrollment(sourceClass).getId()).isEqualTo(originalEnrollment.getId());
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollment(targetClass).getCreatedByTeacherId()).isEqualTo(owner.getId());
    }

    @Test
    void duplicate_active_enrollment_is_rejected_without_adding_a_row() throws Exception {
        mockMvc.perform(add(student, sourceClass).session(ownerSession()))
                .andExpect(status().isBadRequest());

        flushAndClear();
        assertThat(enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(student.getId()))
                .hasSize(1);
        assertThat(enrollment(sourceClass).getId()).isEqualTo(originalEnrollment.getId());
    }

    @Test
    void completing_last_membership_preserves_learning_history_and_makes_student_read_only() throws Exception {
        com.example.entity.Test exam = com.example.entity.Test.builder()
                .title("종료 전 시험").academy(academy).academyClass(sourceClass).build();
        em.persist(exam);
        StudentSubmission submission = StudentSubmission.builder()
                .student(student).test(exam).totalScore(85).build();
        em.persist(submission);
        flushAndClear();

        mockMvc.perform(complete(student, sourceClass).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].endReason").value("MANUAL_COMPLETION"))
                .andExpect(jsonPath("$[0].endedAt").isNotEmpty());

        flushAndClear();
        assertThat(enrollment(sourceClass).getEndedAt()).isNotNull();
        assertThat(em.find(StudentSubmission.class, submission.getId()).getTotalScore()).isEqualTo(85);
        assertThat(em.find(AcademyClass.class, sourceClass.getId()).isEnded()).isFalse();

        MockHttpSession studentSession = studentSession();
        mockMvc.perform(get("/api/auth/me").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudentClassId").value(sourceClass.getId()))
                .andExpect(jsonPath("$.studentClassReadOnly").value(true));
        mockMvc.perform(get("/api/submissions/student/{id}", student.getId()).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].testId").value(exam.getId()));
        mockMvc.perform(post("/api/submissions/me/test/{id}", exam.getId())
                        .with(csrf()).session(studentSession)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void completing_one_membership_leaves_another_active() throws Exception {
        persistEnrollment(student, targetClass);
        flushAndClear();

        mockMvc.perform(complete(student, sourceClass).session(ownerSession()))
                .andExpect(status().isOk());

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.COMPLETED);
        assertThat(enrollment(targetClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        mockMvc.perform(get("/api/auth/me").session(studentSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudentClassId").value(targetClass.getId()))
                .andExpect(jsonPath("$.studentClassReadOnly").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existing_student_session_cannot_edit_homework_immediately_after_completion_or_transfer(boolean transfer)
            throws Exception {
        Homework homework = Homework.builder()
                .title("기존 반 숙제").questionCount(10)
                .academy(academy).academyClass(sourceClass).build();
        em.persist(homework);
        StudentHomework record = StudentHomework.builder()
                .student(student).homework(homework).incorrectCount(2).build();
        em.persist(record);
        flushAndClear();

        MockHttpSession existingSession = studentSession();
        mockMvc.perform(get("/api/auth/me").session(existingSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudentClassId").value(sourceClass.getId()))
                .andExpect(jsonPath("$.studentClassReadOnly").value(false));

        mockMvc.perform((transfer ? transfer(sourceClass, targetClass) : complete(student, sourceClass))
                        .session(ownerSession()))
                .andExpect(status().isOk());

        // No auth/me refresh: the browser still holds its old writable class selection.
        mockMvc.perform(put("/api/student-homeworks/student/{studentId}/homework/{homeworkId}",
                        student.getId(), homework.getId())
                        .with(csrf()).session(existingSession)
                        .contentType("application/json").content("{\"incorrectCount\":0}"))
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(em.find(StudentHomework.class, record.getId()).getIncorrectCount()).isEqualTo(2);
    }

    @Test
    void reactivation_reuses_completed_row_and_clears_completion_metadata() throws Exception {
        StudentClassEnrollment completed = enrollment(sourceClass);
        completed.setStatus(StudentClassEnrollmentStatus.COMPLETED);
        completed.setEndedAt(LocalDateTime.now().minusDays(1));
        completed.setEndReason("MANUAL_COMPLETION");
        flushAndClear();

        mockMvc.perform(add(student, sourceClass).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].endedAt").doesNotExist())
                .andExpect(jsonPath("$[0].endReason").doesNotExist());

        flushAndClear();
        StudentClassEnrollment reactivated = enrollment(sourceClass);
        assertThat(reactivated.getId()).isEqualTo(originalEnrollment.getId());
        assertThat(reactivated.getEndedAt()).isNull();
        assertThat(reactivated.getEndReason()).isNull();
        assertThat(enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(student.getId()))
                .hasSize(1);
    }

    @Test
    void transfer_completes_only_source_and_preserves_third_class_and_learning_record() throws Exception {
        persistEnrollment(student, otherOwnerClass);
        com.example.entity.Test exam = com.example.entity.Test.builder()
                .title("이동 전 시험").academy(academy).academyClass(sourceClass).build();
        em.persist(exam);
        StudentSubmission submission = StudentSubmission.builder()
                .student(student).test(exam).totalScore(90).build();
        em.persist(submission);
        flushAndClear();

        mockMvc.perform(transfer(sourceClass, targetClass).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.COMPLETED);
        assertThat(enrollment(sourceClass).getEndReason()).isEqualTo("TRANSFERRED");
        assertThat(enrollment(sourceClass).getEndedAt()).isNotNull();
        assertThat(enrollment(targetClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollment(otherOwnerClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(em.find(StudentSubmission.class, submission.getId()).getTotalScore()).isEqualTo(90);
    }

    @Test
    void transferring_to_ended_class_leaves_source_active_and_creates_no_target_membership() throws Exception {
        em.find(AcademyClass.class, targetClass.getId()).setEndedAt(LocalDateTime.now());
        flushAndClear();

        mockMvc.perform(transfer(sourceClass, targetClass).session(ownerSession()))
                .andExpect(status().isBadRequest());

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollment(sourceClass).getEndedAt()).isNull();
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), targetClass.getId()))
                .isEmpty();
    }

    @Test
    void transfer_to_same_class_is_rejected() throws Exception {
        mockMvc.perform(transfer(sourceClass, sourceClass).session(ownerSession()))
                .andExpect(status().isBadRequest());
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
    }

    @Test
    void transfer_to_already_active_class_is_rejected_without_completing_source() throws Exception {
        persistEnrollment(student, targetClass);
        flushAndClear();

        mockMvc.perform(transfer(sourceClass, targetClass).session(ownerSession()))
                .andExpect(status().isBadRequest());

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollment(targetClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
    }

    @Test
    void completed_membership_cannot_be_completed_or_transferred_again() throws Exception {
        StudentClassEnrollment completed = enrollment(sourceClass);
        completed.setStatus(StudentClassEnrollmentStatus.COMPLETED);
        completed.setEndedAt(LocalDateTime.now().minusDays(1));
        completed.setEndReason("MANUAL_COMPLETION");
        flushAndClear();

        mockMvc.perform(complete(student, sourceClass).session(ownerSession()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(transfer(sourceClass, targetClass).session(ownerSession()))
                .andExpect(status().isBadRequest());
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), targetClass.getId()))
                .isEmpty();
    }

    @Test
    void ended_class_cannot_be_added() throws Exception {
        em.find(AcademyClass.class, targetClass.getId()).setEndedAt(LocalDateTime.now());
        flushAndClear();

        mockMvc.perform(add(student, targetClass).session(ownerSession()))
                .andExpect(status().isBadRequest());
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), targetClass.getId()))
                .isEmpty();
    }

    @Test
    void teacher_cannot_add_or_transfer_to_another_teachers_class() throws Exception {
        mockMvc.perform(add(student, otherOwnerClass).session(ownerSession()))
                .andExpect(status().isForbidden());
        mockMvc.perform(transfer(sourceClass, otherOwnerClass).session(ownerSession()))
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), otherOwnerClass.getId()))
                .isEmpty();
    }

    @Test
    void teacher_cannot_complete_or_transfer_another_teachers_source() throws Exception {
        persistEnrollment(student, otherOwnerClass);
        flushAndClear();

        mockMvc.perform(complete(student, otherOwnerClass).session(ownerSession()))
                .andExpect(status().isForbidden());
        mockMvc.perform(transfer(otherOwnerClass, targetClass).session(ownerSession()))
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(enrollment(otherOwnerClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
    }

    @Test
    void academy_admin_can_manage_other_teachers_enrollment() throws Exception {
        mockMvc.perform(add(student, otherOwnerClass).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].canManage", containsInAnyOrder(true, true)));
        mockMvc.perform(complete(student, otherOwnerClass).session(adminSession()))
                .andExpect(status().isOk());

        flushAndClear();
        assertThat(enrollment(otherOwnerClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.COMPLETED);
    }

    @Test
    void academy_admin_cannot_add_or_transfer_to_another_academy() throws Exception {
        mockMvc.perform(add(student, otherAcademyClass).session(adminSession()))
                .andExpect(status().isForbidden());
        mockMvc.perform(transfer(sourceClass, otherAcademyClass).session(adminSession()))
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(enrollment(sourceClass).getStatus()).isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), otherAcademyClass.getId()))
                .isEmpty();
    }

    @Test
    void unrelated_student_cannot_be_read_or_added_to_own_class() throws Exception {
        Student unrelated = persistStudent("다른 선생님 학생", otherOwnerClass);
        persistEnrollment(unrelated, otherOwnerClass);
        flushAndClear();

        mockMvc.perform(get("/api/students/{id}/enrollments", unrelated.getId()).session(ownerSession()))
                .andExpect(status().isForbidden());
        mockMvc.perform(add(unrelated, targetClass).session(ownerSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    void another_academys_student_cannot_be_read_or_managed_by_admin() throws Exception {
        Student unrelated = persistStudent("다른 학원 학생", otherAcademyClass);
        persistEnrollment(unrelated, otherAcademyClass);
        flushAndClear();

        mockMvc.perform(get("/api/students/{id}/enrollments", unrelated.getId()).session(adminSession()))
                .andExpect(status().isForbidden());
        mockMvc.perform(add(unrelated, targetClass).session(adminSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    void assistant_can_read_assigned_memberships_but_cannot_mutate_them() throws Exception {
        persistEnrollment(student, targetClass);
        flushAndClear();
        MockHttpSession session = teacherSession(assistant, TeacherAcademyRole.ASSISTANT);

        mockMvc.perform(get("/api/students/{id}/enrollments", student.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].classId").value(sourceClass.getId()))
                .andExpect(jsonPath("$[0].canManage").value(false));
        mockMvc.perform(add(student, targetClass).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(complete(student, sourceClass).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(transfer(sourceClass, targetClass).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollment_list_and_student_dto_expose_only_visible_classes() throws Exception {
        persistEnrollment(student, otherOwnerClass);
        flushAndClear();

        mockMvc.perform(get("/api/students/{id}/enrollments", student.getId()).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].classId").value(sourceClass.getId()))
                .andExpect(jsonPath("$[0].className").value(sourceClass.getName()))
                .andExpect(jsonPath("$[0].startedAt").isNotEmpty());
        mockMvc.perform(get("/api/students/{id}", student.getId()).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments", hasSize(1)))
                .andExpect(jsonPath("$.enrollments[0].classId").value(sourceClass.getId()));
        mockMvc.perform(get("/api/students/{id}", student.getId())
                        .session(teacherSession(otherOwner, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollments", hasSize(1)))
                .andExpect(jsonPath("$.enrollments[0].classId").value(otherOwnerClass.getId()));
    }

    @Test
    void legacy_student_update_cannot_change_class_but_can_update_profile_without_class_id() throws Exception {
        mockMvc.perform(put("/api/students/{id}", student.getId()).with(csrf()).session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"name":"바뀌면 안 됨","grade":"고2","school":"테스트고","classId":%d}
                                """.formatted(targetClass.getId())))
                .andExpect(status().isBadRequest());

        flushAndClear();
        assertThat(em.find(Student.class, student.getId()).getName()).isEqualTo(student.getName());
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), targetClass.getId()))
                .isEmpty();

        mockMvc.perform(put("/api/students/{id}", student.getId()).with(csrf()).session(ownerSession())
                        .contentType("application/json")
                        .content("""
                                {"name":"변경한 학생","grade":"고2","school":"테스트고"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("변경한 학생"))
                .andExpect(jsonPath("$.enrollments", hasSize(1)));
    }

    @Test
    void teacher_cannot_delete_student_with_cross_class_history() throws Exception {
        persistEnrollment(student, otherOwnerClass);
        flushAndClear();

        mockMvc.perform(delete("/api/students/{id}", student.getId()).with(csrf()).session(ownerSession()))
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(em.find(Student.class, student.getId())).isNotNull();
        assertThat(enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(student.getId()))
                .hasSize(2);
    }

    @Test
    void academy_admin_can_delete_student_and_enrollments() throws Exception {
        persistEnrollment(student, otherOwnerClass);
        flushAndClear();

        mockMvc.perform(delete("/api/students/{id}", student.getId()).with(csrf()).session(adminSession()))
                .andExpect(status().isNoContent());

        flushAndClear();
        assertThat(em.find(Student.class, student.getId())).isNull();
        assertThat(enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(student.getId())).isEmpty();
    }

    @Test
    void adding_to_legacy_student_backfills_original_class_before_new_membership() throws Exception {
        Student legacyStudent = persistStudent("이전 구조 학생", sourceClass);
        flushAndClear();

        mockMvc.perform(add(legacyStudent, targetClass).session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].classId", containsInAnyOrder(
                        sourceClass.getId().intValue(), targetClass.getId().intValue())));

        flushAndClear();
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(legacyStudent.getId(), sourceClass.getId()))
                .get().extracting(StudentClassEnrollment::getStatus)
                .isEqualTo(StudentClassEnrollmentStatus.ACTIVE);
        assertThat(enrollmentRepository.findByStudentIdAndAcademyClassId(legacyStudent.getId(), targetClass.getId()))
                .isPresent();
    }

    private MockHttpServletRequestBuilder add(Student targetStudent, AcademyClass academyClass) {
        return post("/api/students/{id}/enrollments", targetStudent.getId())
                .with(csrf()).contentType("application/json")
                .content("{\"classId\":%d}".formatted(academyClass.getId()));
    }

    private MockHttpServletRequestBuilder complete(Student targetStudent, AcademyClass academyClass) {
        return post("/api/students/{id}/enrollments/{classId}/complete", targetStudent.getId(), academyClass.getId())
                .with(csrf());
    }

    private MockHttpServletRequestBuilder transfer(AcademyClass source, AcademyClass target) {
        return post("/api/students/{id}/enrollments/{classId}/transfer", student.getId(), source.getId())
                .with(csrf()).contentType("application/json")
                .content("{\"classId\":%d}".formatted(target.getId()));
    }

    private StudentClassEnrollment enrollment(AcademyClass academyClass) {
        return enrollmentRepository.findByStudentIdAndAcademyClassId(student.getId(), academyClass.getId())
                .orElseThrow();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Teacher persistTeacher(String username, TeacherAcademyRole role) {
        Teacher teacher = new Teacher();
        teacher.setUsername(username);
        teacher.setName(username);
        teacher.setPin("123456");
        em.persist(teacher);
        em.persist(TeacherAcademy.builder()
                .teacherId(teacher.getId()).academyId(academy.getId()).role(role).build());
        return teacher;
    }

    private AcademyClass persistClass(String name, Academy classAcademy, Teacher teacher) {
        AcademyClass academyClass = AcademyClass.builder()
                .name(name).academy(classAcademy).ownerTeacherId(teacher.getId()).build();
        em.persist(academyClass);
        return academyClass;
    }

    private Student persistStudent(String name, AcademyClass academyClass) {
        Student newStudent = Student.builder()
                .name(name).grade("고1").school("테스트고")
                .academy(academyClass.getAcademy()).academyClass(academyClass).build();
        em.persist(newStudent);
        return newStudent;
    }

    private StudentClassEnrollment persistEnrollment(Student targetStudent, AcademyClass academyClass) {
        StudentClassEnrollment enrollment = StudentClassEnrollment.builder()
                .student(targetStudent).academyClass(academyClass)
                .status(StudentClassEnrollmentStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusMonths(1)).build();
        em.persist(enrollment);
        return enrollment;
    }

    private MockHttpSession ownerSession() {
        return teacherSession(owner, TeacherAcademyRole.TEACHER);
    }

    private MockHttpSession adminSession() {
        return teacherSession(admin, TeacherAcademyRole.ACADEMY_ADMIN);
    }

    private MockHttpSession teacherSession(Teacher teacher, TeacherAcademyRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", role.name());
        return session;
    }

    private MockHttpSession studentSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("studentAcademyId", academy.getId());
        session.setAttribute("activeStudentClassId", sourceClass.getId());
        return session;
    }
}
