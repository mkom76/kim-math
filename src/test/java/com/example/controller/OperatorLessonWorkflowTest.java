package com.example.controller;

import com.example.entity.*;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentLessonRepository;
import jakarta.persistence.EntityManager;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperatorLessonWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired StudentHomeworkRepository progressRepository;
    @Autowired StudentLessonRepository recordRepository;
    private Academy academy;
    private Teacher teacher;
    private AcademyClass firstClass;
    private AcademyClass secondClass;
    private Lesson first;
    private Lesson second;
    private Homework homework;
    private Student firstStudent;
    private Student secondStudent;

    @BeforeEach
    void setUp() {
        academy = persist(Academy.builder().name("운영 학원").build());
        teacher = persist(Teacher.builder().name("담당 선생님").username("operator-workflow").pin("123456").build());
        persist(TeacherAcademy.builder().teacherId(teacher.getId()).academyId(academy.getId()).role(TeacherAcademyRole.TEACHER).build());
        firstClass = clazz("기본반", teacher.getId());
        secondClass = clazz("심화반", teacher.getId());
        first = lesson(firstClass, 20);
        second = lesson(secondClass, 21);
        firstStudent = student("가학생", firstClass);
        secondStudent = student("나학생", secondClass);
        homework = persist(Homework.builder().title("공통 숙제").questionCount(10).academy(academy).academyClass(firstClass).build());
        em.flush();
    }

    @Test
    void assignsSameHomeworkAcrossClassesAndUpdatesContentEverywhere() throws Exception {
        assign(homework, List.of(first, second), true);
        mvc.perform(put("/api/homeworks/{id}", homework.getId()).session(session())
                        .contentType("application/json").content("{\"title\":\"수정한 공통 숙제\",\"questionCount\":20}"))
                .andExpect(status().isOk());
        for (Lesson lesson : List.of(first, second)) {
            mvc.perform(get("/api/lessons/{id}/homeworks", lesson.getId()).session(session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(homework.getId()))
                    .andExpect(jsonPath("$[0].title").value("수정한 공통 숙제"))
                    .andExpect(jsonPath("$[0].questionCount").value(20));
        }
        assertThat(progressRepository.findByStudentIdAndHomeworkId(firstStudent.getId(), homework.getId())).isPresent();
        assertThat(progressRepository.findByStudentIdAndHomeworkId(secondStudent.getId(), homework.getId())).isPresent();
    }

    @Test
    void assignmentIsIdempotentAndPreservesAnotherHomework() throws Exception {
        Homework existing = persist(Homework.builder().title("기존 숙제").questionCount(10).academy(academy)
                .academyClass(secondClass).lesson(second).build());
        persist(StudentHomework.builder().student(secondStudent).homework(existing).incorrectCount(0).build());
        em.flush();
        em.clear();
        assign(homework, List.of(first, second, first), true);
        assign(homework, List.of(first, second), true);
        mvc.perform(get("/api/lessons/{id}/assignments", second.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedHomeworkId").value(existing.getId()));
        mvc.perform(get("/api/homeworks/{id}/lessons", homework.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        assertThat(progressRepository.findByStudentIdAndHomeworkId(secondStudent.getId(), homework.getId())).isEmpty();
    }

    @Test
    void removingOneLessonsAssignmentPreservesOtherLessonsProgress() throws Exception {
        Lesson later = lesson(firstClass, 22);
        assign(homework, List.of(first, later), true);
        mvc.perform(post("/api/lessons/{id}/assign-homeworks", first.getId()).session(session())
                        .contentType("application/json").content("{\"" + firstStudent.getId() + "\":null}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lessons/{id}/assignments", first.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedHomeworkId").isEmpty());
        mvc.perform(get("/api/lessons/{id}/assignments", later.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedHomeworkId").value(homework.getId()));
        assertThat(progressRepository.findByStudentIdAndHomeworkId(firstStudent.getId(), homework.getId())).isPresent();
    }

    @Test
    void gradingApiRequiresTheExactRecordWhenHomeworkAppearsInSeveralLessons() throws Exception {
        Lesson later = lesson(firstClass, 22);
        assign(homework, List.of(first, later), true);
        Long firstRecord = progressRepository.findByStudentIdAndHomeworkIdAndLessonId(firstStudent.getId(), homework.getId(), first.getId()).orElseThrow().getId();
        Long laterRecord = progressRepository.findByStudentIdAndHomeworkIdAndLessonId(firstStudent.getId(), homework.getId(), later.getId()).orElseThrow().getId();
        mvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}", firstStudent.getId(), homework.getId())
                        .session(session()).contentType("application/json").content("{\"incorrectCount\":2,\"unsolvedCount\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}", firstStudent.getId(), homework.getId())
                        .param("studentHomeworkId", firstRecord.toString()).session(session())
                        .contentType("application/json").content("{\"incorrectCount\":2,\"unsolvedCount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lessonId").value(first.getId()))
                .andExpect(jsonPath("$.completion").value(80));
        mvc.perform(put("/api/student-homeworks/student/{sid}/homework/{hid}/follow-up", firstStudent.getId(), homework.getId())
                        .param("studentHomeworkId", laterRecord.toString()).session(session())
                        .contentType("application/json").content("{\"followUp\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lessons/{id}/assignments", first.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].completion").value(80))
                .andExpect(jsonPath("$[0].followUpFlag").value(false));
        mvc.perform(get("/api/lessons/{id}/assignments", later.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].completion").isEmpty())
                .andExpect(jsonPath("$[0].followUpFlag").value(true));
    }

    @Test
    void detachingOneLessonRetainsHomeworkAndOtherAssignments() throws Exception {
        assign(homework, List.of(first, second), true);
        mvc.perform(delete("/api/lessons/{lessonId}/homeworks/{homeworkId}", first.getId(), homework.getId()).session(session()))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(em.find(Homework.class, homework.getId())).isNotNull();
        mvc.perform(get("/api/lessons/{id}/homeworks", first.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/lessons/{id}/assignments", second.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedHomeworkId").value(homework.getId()));
    }

    @Test
    void deletingDetachedLessonWithRetainedLearningHistoryIsRejected() throws Exception {
        assign(homework, List.of(first), true);
        mvc.perform(delete("/api/lessons/{lessonId}/homeworks/{homeworkId}", first.getId(), homework.getId()).session(session()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/lessons/{id}", first.getId()).session(session()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("학생 숙제 기록이 남아 있는 수업은 삭제할 수 없습니다"));
        assertThat(em.find(Lesson.class, first.getId())).isNotNull();
    }

    @Test
    void rejectsUnauthorizedTargetAndRollsBackAllAssignments() throws Exception {
        AcademyClass forbidden = clazz("다른 선생님 반", teacher.getId() + 1000);
        Lesson forbiddenLesson = lesson(forbidden, 22);
        mvc.perform(post("/api/homeworks/{id}/lessons", homework.getId()).session(session())
                        .contentType("application/json").content(body(List.of(first, forbiddenLesson), true)))
                .andExpect(status().isForbidden());
        em.clear();
        assertThat(em.find(Lesson.class, first.getId()).getHomeworks()).isEmpty();
    }

    @Test
    void acceptsLinkWithoutStudentAssignmentAndRejectsEndedClass() throws Exception {
        assign(homework, List.of(first), false);
        assertThat(progressRepository.findByStudentId(firstStudent.getId())).isEmpty();
        secondClass.setEndedAt(java.time.LocalDateTime.now());
        em.flush();
        mvc.perform(post("/api/homeworks/{id}/lessons", homework.getId()).session(session())
                        .contentType("application/json").content(body(List.of(second), true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sharedHomeworkAppearsInStudentClassContextAndFeedback() throws Exception {
        assign(homework, List.of(first, second), true);
        progressRepository.findByStudentIdAndHomeworkId(secondStudent.getId(), homework.getId()).orElseThrow().setIncorrectCount(2);
        mvc.perform(get("/api/daily-feedback/student/{studentId}/lesson/{lessonId}", secondStudent.getId(), second.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.todayHomework.homeworkId").value(homework.getId()))
                .andExpect(jsonPath("$.todayHomework.completion").value(80));
        assertThat(progressRepository.findByStudentIdAndHomeworkAcademyClassId(secondStudent.getId(), secondClass.getId()))
                .extracting(progress -> progress.getHomework().getId()).containsExactly(homework.getId());
        MockHttpSession studentSession = new MockHttpSession();
        studentSession.setAttribute("userId", secondStudent.getId());
        studentSession.setAttribute("userRole", "STUDENT");
        studentSession.setAttribute("studentAcademyId", academy.getId());
        studentSession.setAttribute("activeStudentClassId", secondClass.getId());
        mvc.perform(get("/api/student-homeworks/student/{id}", secondStudent.getId()).session(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].homeworkId").value(homework.getId()));
    }

    @Test
    void feedbackRosterIncludesUnwrittenStudentsAndReflectsEdits() throws Exception {
        Student other = student("다학생", firstClass);
        persist(StudentLesson.builder().student(firstStudent).lesson(first).instructorFeedback("AI 초안")
                .feedbackAuthor("AI").isAiFeedback(true).attendanceStatus(AttendanceStatus.PRESENT).build());
        em.flush();
        mvc.perform(get("/api/daily-feedback/lesson/{id}/students", first.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].instructorFeedback").value("AI 초안"))
                .andExpect(jsonPath("$[1].studentId").value(other.getId()))
                .andExpect(jsonPath("$[1].instructorFeedback").isEmpty());
        mvc.perform(put("/api/daily-feedback/student/{studentId}/lesson/{lessonId}", firstStudent.getId(), first.getId())
                        .session(session()).contentType("application/json")
                        .content("{\"feedback\":\"확인 후 수정한 피드백\",\"authorName\":\"담당 선생님\",\"isAiFeedback\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/daily-feedback/lesson/{id}/students", first.getId()).session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].instructorFeedback").value("확인 후 수정한 피드백"))
                .andExpect(jsonPath("$[0].feedbackAuthor").value("담당 선생님"));
        assertThat(recordRepository.findByStudentIdAndLessonId(firstStudent.getId(), first.getId()).orElseThrow().getIsAiFeedback()).isFalse();
    }

    @Test
    void rejectsFeedbackRosterOutsideTeachersClass() throws Exception {
        Lesson forbidden = lesson(clazz("접근 불가", teacher.getId() + 1000), 22);
        mvc.perform(get("/api/daily-feedback/lesson/{id}/students", forbidden.getId()).session(session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharedClassTeacherCanFindAndEditHomeworkWhileOtherAcademiesStayHidden() throws Exception {
        Teacher otherTeacher = persist(Teacher.builder().name("다른 담당").username("shared-class-teacher").pin("123456").build());
        persist(TeacherAcademy.builder().teacherId(otherTeacher.getId()).academyId(academy.getId()).role(TeacherAcademyRole.TEACHER).build());
        secondClass.setOwnerTeacherId(otherTeacher.getId());
        for (AcademyClass clazz : List.of(firstClass, secondClass)) {
            persist(StudentClassEnrollment.builder().student(firstStudent).academyClass(clazz)
                    .status(StudentClassEnrollmentStatus.ACTIVE).startedAt(java.time.LocalDateTime.now()).build());
        }
        MockHttpSession admin = session();
        admin.setAttribute("activeRole", "ACADEMY_ADMIN");
        mvc.perform(post("/api/homeworks/{id}/lessons", homework.getId()).session(admin)
                        .contentType("application/json").content(body(List.of(first, second), true)))
                .andExpect(status().isOk());
        Academy foreign = persist(Academy.builder().name("외부 학원").build());
        AcademyClass foreignClass = persist(AcademyClass.builder().name("외부 반").academy(foreign).ownerTeacherId(otherTeacher.getId()).build());
        Homework foreignHomework = persist(Homework.builder().title("외부 숙제").questionCount(10).academy(foreign).academyClass(foreignClass).build());
        em.flush();
        MockHttpSession other = session();
        other.setAttribute("userId", otherTeacher.getId());
        mvc.perform(get("/api/homeworks").session(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(homework.getId()));
        mvc.perform(get("/api/homeworks/{id}/lessons", homework.getId()).session(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second.getId()));
        mvc.perform(put("/api/homeworks/{id}", homework.getId()).session(other).contentType("application/json")
                        .content("{\"title\":\"공유된 수정\",\"questionCount\":12}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/homeworks/{id}/problems", foreignHomework.getId()).session(other))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/lessons/{id}/assign-homeworks", first.getId()).session(session())
                        .contentType("application/json").content("{\"" + firstStudent.getId() + "\":null}"))
                .andExpect(status().isOk());
        assertThat(progressRepository.findByStudentIdAndHomeworkIdAndLessonId(firstStudent.getId(), homework.getId(), first.getId())).isEmpty();
        mvc.perform(get("/api/lessons/{id}/assignments", second.getId()).session(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedHomeworkId").value(homework.getId()));
    }

    @Test
    void protectsSubmittedAssignmentsAndRejectsStudentsFromAnotherClass() throws Exception {
        assign(homework, List.of(first), true);
        progressRepository.findByStudentIdAndHomeworkId(firstStudent.getId(), homework.getId()).orElseThrow().setIncorrectCount(0);
        mvc.perform(post("/api/lessons/{id}/assign-homeworks", first.getId()).session(session())
                        .contentType("application/json").content("{\"" + firstStudent.getId() + "\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/lessons/{id}/assign-homeworks", first.getId()).session(session())
                        .contentType("application/json").content("{\"" + secondStudent.getId() + "\":" + homework.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blocksDeletingSharedHomeworkUntilAllLessonsAreDetached() throws Exception {
        assign(homework, List.of(first, second), true);
        mvc.perform(delete("/api/homeworks/{id}", homework.getId()).session(session())).andExpect(status().isBadRequest());
        for (Lesson lesson : List.of(first, second)) {
            mvc.perform(delete("/api/lessons/{lessonId}/homeworks/{homeworkId}", lesson.getId(), homework.getId()).session(session()))
                    .andExpect(status().isOk());
        }
        mvc.perform(delete("/api/homeworks/{id}", homework.getId()).session(session())).andExpect(status().isNoContent());
        em.flush();
        assertThat(em.find(Homework.class, homework.getId())).isNull();
    }

    private void assign(Homework source, List<Lesson> lessons, boolean assignStudents) throws Exception {
        mvc.perform(post("/api/homeworks/{id}/lessons", source.getId()).session(session())
                        .contentType("application/json").content(body(lessons, assignStudents)))
                .andExpect(status().isOk());
    }
    private String body(List<Lesson> lessons, boolean assignStudents) {
        return "{\"lessonIds\":" + lessons.stream().map(Lesson::getId).toList() + ",\"assignStudents\":" + assignStudents + "}";
    }
    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("activeAcademyId", academy.getId());
        session.setAttribute("activeRole", "TEACHER");
        return session;
    }
    private AcademyClass clazz(String name, Long owner) {
        return persist(AcademyClass.builder().name(name).academy(academy).ownerTeacherId(owner).build());
    }
    private Lesson lesson(AcademyClass clazz, int day) {
        return persist(Lesson.builder().academy(academy).academyClass(clazz).lessonDate(LocalDate.of(2026, 10, day)).build());
    }
    private Student student(String name, AcademyClass clazz) {
        return persist(Student.builder().name(name).grade("고1").school("고등학교").academy(academy).academyClass(clazz).pin("1111").build());
    }
    private <T> T persist(T entity) { em.persist(entity); return entity; }
}
