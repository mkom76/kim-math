package com.example.controller;

import com.example.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherMultiClassAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    private Academy academy;
    private Academy otherAcademy;
    private Teacher firstTeacher;
    private Teacher secondTeacher;
    private Teacher assistant;
    private Teacher unrelatedTeacher;
    private Teacher admin;
    private AcademyClass firstClass;
    private AcademyClass secondClass;
    private Student student;
    private Student legacyStudent;
    private Student foreignStudent;
    private StudentClassEnrollment firstEnrollment;
    private StudentClassEnrollment secondEnrollment;

    @BeforeEach
    void setUp() {
        academy = persist(Academy.builder().name("다중 반 학원").build());
        otherAcademy = persist(Academy.builder().name("다른 학원").build());
        firstTeacher = teacher("first", TeacherAcademyRole.TEACHER);
        secondTeacher = teacher("second", TeacherAcademyRole.TEACHER);
        assistant = teacher("assistant", TeacherAcademyRole.ASSISTANT);
        unrelatedTeacher = teacher("unrelated", TeacherAcademyRole.TEACHER);
        admin = teacher("admin", TeacherAcademyRole.ACADEMY_ADMIN);
        firstClass = academyClass("기존 반", academy, firstTeacher);
        secondClass = academyClass("추가 반", academy, secondTeacher);
        persist(ClassAssistant.builder().classId(secondClass.getId()).teacherId(assistant.getId()).build());
        student = student("다중 반 학생", firstClass);
        legacyStudent = student("마이그레이션 이전 학생", firstClass);
        foreignStudent = student("다른 학원 학생", academyClass("다른 학원 반", otherAcademy, secondTeacher));
        firstEnrollment = enroll(student, firstClass, StudentClassEnrollmentStatus.ACTIVE);
        secondEnrollment = enroll(student, secondClass, StudentClassEnrollmentStatus.ACTIVE);
        em.flush();
    }

    @Test
    void secondaryClassTeacherCanListAndReadStudent() throws Exception {
        assertVisibleStudent(secondTeacher, TeacherAcademyRole.TEACHER);
    }

    @Test
    void secondaryClassAssistantCanListAndReadStudent() throws Exception {
        assertVisibleStudent(assistant, TeacherAcademyRole.ASSISTANT);
    }

    @Test
    void completedEnrollmentRetainsStudentAndLearningHistoryAccess() throws Exception {
        secondEnrollment.setStatus(StudentClassEnrollmentStatus.COMPLETED);
        secondEnrollment.setEndedAt(LocalDateTime.now());
        secondClass.setEndedAt(LocalDateTime.now());
        LearningRecords records = learningRecords(secondClass);
        em.flush();

        assertVisibleStudent(secondTeacher, TeacherAcademyRole.TEACHER);
        mockMvc.perform(get("/api/submissions/{id}", records.submission().getId())
                        .session(session(secondTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/lessons/student/{id}", student.getId())
                        .session(session(secondTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].classId").value(secondClass.getId()));
    }

    @Test
    void withdrawnLegacyClassDoesNotGrantAccessButUnmigratedStudentsRemainVisible() throws Exception {
        firstEnrollment.setStatus(StudentClassEnrollmentStatus.WITHDRAWN);
        em.flush();

        mockMvc.perform(get("/api/students/{id}", student.getId())
                        .session(session(firstTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students").session(session(firstTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(legacyStudent.getId()));
        mockMvc.perform(get("/api/students/{id}", legacyStudent.getId())
                        .session(session(firstTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk());
    }

    @Test
    void scheduledEnrollmentDoesNotGrantAccess() throws Exception {
        secondEnrollment.setStatus(StudentClassEnrollmentStatus.SCHEDULED);
        em.flush();

        assertDeniedStudent(secondTeacher);
    }

    @Test
    void unrelatedTeacherCannotAccessStudent() throws Exception {
        assertDeniedStudent(unrelatedTeacher);
    }

    @Test
    void academyBoundaryAppliesToTeachersAndAdmins() throws Exception {
        for (Teacher teacher : List.of(secondTeacher, admin)) {
            TeacherAcademyRole role = teacher == admin
                    ? TeacherAcademyRole.ACADEMY_ADMIN : TeacherAcademyRole.TEACHER;
            mockMvc.perform(get("/api/students/{id}", foreignStudent.getId()).session(session(teacher, role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void enrollmentInAnotherAcademyCannotGrantStudentAccess() throws Exception {
        enroll(legacyStudent, foreignStudent.getAcademyClass(), StudentClassEnrollmentStatus.ACTIVE);
        em.flush();

        assertVisibleStudent(secondTeacher, TeacherAcademyRole.TEACHER);
        mockMvc.perform(get("/api/students/{id}", legacyStudent.getId())
                        .session(session(secondTeacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void eachTeacherSeesOnlyTheirOwnLearningRecordsForTheSharedStudent() throws Exception {
        LearningRecords first = learningRecords(firstClass);
        LearningRecords second = learningRecords(secondClass);
        em.flush();

        for (LearningRecords records : List.of(first, second)) {
            Teacher teacher = records == first ? firstTeacher : secondTeacher;
            MockHttpSession session = session(teacher, TeacherAcademyRole.TEACHER);
            mockMvc.perform(get("/api/submissions/student/{id}", student.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(records.submission().getId()));
            mockMvc.perform(get("/api/student-homeworks/student/{id}", student.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].homeworkId").value(records.homework().getId()));
            mockMvc.perform(get("/api/students/{id}/videos/progress", student.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].videoId").value(records.video().getId()));
            mockMvc.perform(get("/api/lessons/student/{id}", student.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(records.lesson().getId()));
            mockMvc.perform(get("/api/lessons/attendance/student/{id}", student.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalLessons").value(1))
                    .andExpect(jsonPath("$.presentCount").value(1));
        }
    }

    @Test
    void studentAccessDoesNotAllowReadingOrGradingAnotherClassSubmission() throws Exception {
        LearningRecords records = learningRecords(firstClass);
        em.flush();
        MockHttpSession session = session(secondTeacher, TeacherAcademyRole.TEACHER);

        mockMvc.perform(get("/api/submissions/{id}", records.submission().getId()).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/submissions/details/{id}/grade", records.detail().getId())
                        .session(session).contentType("application/json")
                        .content("{\"earnedPoints\":5,\"teacherComment\":\"수정\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allStudentLearningEntityFiltersUseTheirRecordClass() {
        learningRecords(firstClass);
        LearningRecords second = learningRecords(secondClass);
        em.flush();
        em.clear();
        Session session = em.unwrap(Session.class);
        session.enableFilter("academyFilter").setParameter("academyId", academy.getId());
        session.enableFilter("ownerFilter").setParameter("teacherId", secondTeacher.getId());

        assertThat(em.createQuery("from StudentSubmission", StudentSubmission.class).getResultList())
                .extracting(StudentSubmission::getId).containsExactly(second.submission().getId());
        assertThat(em.createQuery("from StudentSubmissionDetail", StudentSubmissionDetail.class).getResultList())
                .extracting(StudentSubmissionDetail::getId).containsExactly(second.detail().getId());
        assertThat(em.createQuery("from StudentHomework", StudentHomework.class).getResultList())
                .extracting(sh -> sh.getHomework().getId()).containsExactly(second.homework().getId());
        assertThat(em.createQuery("from StudentLesson", StudentLesson.class).getResultList())
                .extracting(sl -> sl.getLesson().getId()).containsExactly(second.lesson().getId());
        assertThat(em.createQuery("from StudentVideoProgress", StudentVideoProgress.class).getResultList())
                .extracting(progress -> progress.getLessonVideo().getId()).containsExactly(second.video().getId());
        assertThat(em.createQuery("from ClinicRegistration", ClinicRegistration.class).getResultList())
                .extracting(registration -> registration.getClinic().getId()).containsExactly(second.clinic().getId());
        assertThat(em.createQuery("from ClinicHomeworkProgress", ClinicHomeworkProgress.class).getResultList())
                .extracting(progress -> progress.getClinic().getId()).containsExactly(second.clinic().getId());
    }

    private void assertVisibleStudent(Teacher teacher, TeacherAcademyRole role) throws Exception {
        mockMvc.perform(get("/api/students").session(session(teacher, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(student.getId()));
        mockMvc.perform(get("/api/students/{id}", student.getId()).session(session(teacher, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()));
    }

    private void assertDeniedStudent(Teacher teacher) throws Exception {
        mockMvc.perform(get("/api/students/{id}", student.getId())
                        .session(session(teacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students").session(session(teacher, TeacherAcademyRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private Teacher teacher(String username, TeacherAcademyRole role) {
        Teacher teacher = persist(Teacher.builder().username(username).name(username).pin("123456").build());
        persist(TeacherAcademy.builder().teacherId(teacher.getId()).academyId(academy.getId()).role(role).build());
        return teacher;
    }

    private AcademyClass academyClass(String name, Academy ownerAcademy, Teacher teacher) {
        return persist(AcademyClass.builder().name(name).academy(ownerAcademy).ownerTeacherId(teacher.getId()).build());
    }

    private Student student(String name, AcademyClass clazz) {
        return persist(Student.builder().name(name).grade("고1").school("테스트고")
                .academy(clazz.getAcademy()).academyClass(clazz).build());
    }

    private StudentClassEnrollment enroll(Student enrolledStudent, AcademyClass clazz, StudentClassEnrollmentStatus status) {
        return persist(StudentClassEnrollment.builder().student(enrolledStudent).academyClass(clazz)
                .status(status).startedAt(LocalDateTime.now().minusMonths(1)).build());
    }

    private MockHttpSession session(Teacher teacher, TeacherAcademyRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", role.name());
        return session;
    }

    private LearningRecords learningRecords(AcademyClass clazz) {
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(clazz)
                .lessonDate(LocalDate.of(2026, 9, 1)).build());
        com.example.entity.Test test = persist(com.example.entity.Test.builder().academy(academy).academyClass(clazz)
                .title(clazz.getName() + " 시험").build());
        StudentSubmission submission = persist(StudentSubmission.builder().student(student).test(test)
                .totalScore(80).submittedAt(LocalDateTime.now()).build());
        TestQuestion question = persist(TestQuestion.builder().test(test).number(1).answer("1")
                .points(10.0).questionType(QuestionType.ESSAY).build());
        StudentSubmissionDetail detail = persist(StudentSubmissionDetail.builder().submission(submission)
                .question(question).studentAnswer("1").earnedPoints(8.0).build());
        Homework homework = persist(Homework.builder().academy(academy).academyClass(clazz)
                .title(clazz.getName() + " 숙제").questionCount(10).build());
        persist(StudentHomework.builder().student(student).homework(homework).incorrectCount(2).build());
        persist(StudentLesson.builder().student(student).lesson(lesson).attendanceStatus(AttendanceStatus.PRESENT).build());
        LessonVideo video = persist(LessonVideo.builder().lesson(lesson).youtubeUrl("https://www.youtube.com/watch?v=test")
                .youtubeVideoId("test").orderIndex(0).build());
        persist(StudentVideoProgress.builder().student(student).lessonVideo(video)
                .watchedTime(10).duration(100).completed(false).build());
        Clinic clinic = persist(Clinic.builder().academyClass(clazz).clinicDate(LocalDate.of(2026, 9, 1))
                .clinicTime(LocalTime.of(18, 0)).build());
        persist(ClinicRegistration.builder().clinic(clinic).student(student).build());
        persist(ClinicHomeworkProgress.builder().clinic(clinic).student(student).homework(homework).build());
        return new LearningRecords(lesson, submission, detail, homework, video, clinic);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private record LearningRecords(Lesson lesson, StudentSubmission submission, StudentSubmissionDetail detail,
                                   Homework homework, LessonVideo video, Clinic clinic) {}
}
