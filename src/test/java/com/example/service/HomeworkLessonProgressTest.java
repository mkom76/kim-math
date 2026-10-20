package com.example.service;

import com.example.config.security.TenantContext;
import com.example.entity.*;
import com.example.exception.ForbiddenException;
import com.example.repository.ClinicHomeworkProgressRepository;
import com.example.repository.StudentHomeworkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeworkLessonProgressTest {
    @Autowired EntityManager em;
    @Autowired LessonHomeworkAssignmentService assignments;
    @Autowired StudentHomeworkRepository records;
    @Autowired StudentHomeworkService progress;
    @Autowired DailyFeedbackService feedback;
    @Autowired ClinicService clinics;
    @Autowired ClinicHomeworkProgressRepository snapshots;
    private Academy academy;
    private AcademyClass clazz;
    private Student student;
    private Homework homework;
    private Lesson first;
    private Lesson second;

    @BeforeEach
    void setUp() {
        academy = persist(Academy.builder().name("수업별 진도 학원").build());
        clazz = persist(AcademyClass.builder().name("기본반").academy(academy).ownerTeacherId(100L).build());
        student = persist(Student.builder().name("학생").grade("고1").school("학교").pin("1111")
                .academy(academy).academyClass(clazz).build());
        homework = persist(Homework.builder().title("공통 숙제").questionCount(10).academy(academy).academyClass(clazz).build());
        first = lesson(clazz, 20);
        second = lesson(clazz, 21);
        assignments.assign(first, Map.of(student.getId(), homework.getId()));
        assignments.assign(second, Map.of(student.getId(), homework.getId()));
        TenantContext.set(100L, academy.getId(), TeacherAcademyRole.ACADEMY_ADMIN);
    }

    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void sameHomeworkHasIndependentGradesQuestionsFlagsAndFeedbackInEachLesson() {
        Long firstId = record(first).getId();
        Long secondId = record(second).getId();
        assertThat(firstId).isNotEqualTo(secondId);
        progress.updateIncorrectCount(student.getId(), homework.getId(), 2, 1, "1,2", "3", firstId);
        progress.setFollowUp(student.getId(), homework.getId(), true, firstId);
        TenantContext.setStudent(student.getId(), academy.getId(), clazz.getId());
        progress.updateQuestionedQuestions(student.getId(), homework.getId(), "4,5", secondId);

        var result = feedback.getDailyFeedback(student.getId(), first.getId());
        assertThat(result.getTodayHomework().getStudentHomeworkId()).isEqualTo(firstId);
        assertThat(result.getTodayHomework().getCompletion()).isEqualTo(70);
        assertThat(result.getTodayHomework().getQuestionedQuestions()).isNull();
        assertThat(result.getNextHomework().getStudentHomeworkId()).isEqualTo(secondId);
        assertThat(result.getNextHomework().getCompletion()).isNull();
        assertThat(result.getNextHomework().getQuestionedQuestions()).isEqualTo("4,5");
        assertThat(progress.getFollowUpsByStudent(student.getId())).extracting(row -> row.getId()).containsExactly(firstId);

        // Content remains shared while learning state stays separate.
        homework.setTitle("수정된 공통 숙제");
        assertThat(feedback.getDailyFeedback(student.getId(), second.getId()).getTodayHomework().getHomeworkTitle())
                .isEqualTo("수정된 공통 숙제");
        assertThat(record(second).getIncorrectCount()).isNull();
        assertThat(record(second).getFollowUpFlag()).isFalse();
    }

    @Test
    void ambiguousLegacyWritesAreRejectedWithoutChangingAnyLesson() {
        assertThatThrownBy(() -> progress.updateIncorrectCount(student.getId(), homework.getId(), 0, 0, "", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("수업");
        assertThatThrownBy(() -> progress.setFollowUp(student.getId(), homework.getId(), true))
                .isInstanceOf(IllegalArgumentException.class);
        TenantContext.setStudent(student.getId(), academy.getId(), clazz.getId());
        assertThatThrownBy(() -> progress.updateQuestionedQuestions(student.getId(), homework.getId(), "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(record(first).getIncorrectCount()).isNull();
        assertThat(record(second).getQuestionedQuestions()).isNull();
    }

    @Test
    void clinicTracksBothLessonsAndKeepsTheirBeforeAndAfterSnapshotsSeparate() {
        StudentHomework a = record(first);
        StudentHomework b = record(second);
        a.setIncorrectCount(4);
        b.setIncorrectCount(2);
        Clinic clinic = persist(Clinic.builder().academyClass(clazz).clinicDate(LocalDate.of(2026, 10, 22))
                .clinicTime(LocalTime.of(14, 0)).build());
        clinics.startClinic(clinic.getId());
        clinics.startClinic(clinic.getId());
        assertThat(snapshots.findByClinicId(clinic.getId()).stream().map(ClinicHomeworkProgress::getId).toList()).hasSize(2);
        progress.updateIncorrectCount(student.getId(), homework.getId(), 0, 0, "", "", a.getId());
        clinics.endClinic(clinic.getId());
        var firstSnapshot = snapshots.findByClinicIdAndStudentHomeworkId(clinic.getId(), a.getId()).orElseThrow();
        var secondSnapshot = snapshots.findByClinicIdAndStudentHomeworkId(clinic.getId(), b.getId()).orElseThrow();
        assertThat(firstSnapshot.getIncorrectCountBefore()).isEqualTo(4);
        assertThat(firstSnapshot.getIncorrectCountAfter()).isZero();
        assertThat(secondSnapshot.getIncorrectCountBefore()).isEqualTo(2);
        assertThat(secondSnapshot.getIncorrectCountAfter()).isEqualTo(2);
        assertThat(clinics.getClinicDetail(clinic.getId()).getStudents().get(0).getHomeworks())
                .extracting(row -> row.getStudentHomeworkId()).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void sharedContentNeverGrantsAccessToAnotherLessonsRecordAndClinicStaysWithinItsClass() {
        AcademyClass other = persist(AcademyClass.builder().name("심화반").academy(academy).ownerTeacherId(200L).build());
        for (AcademyClass member : List.of(clazz, other)) persist(StudentClassEnrollment.builder().student(student)
                .academyClass(member).status(StudentClassEnrollmentStatus.ACTIVE).startedAt(java.time.LocalDateTime.now()).build());
        Lesson otherLesson = lesson(other, 22);
        assignments.assign(otherLesson, Map.of(student.getId(), homework.getId()));
        Long foreignRecord = record(otherLesson).getId();
        TenantContext.set(100L, academy.getId(), TeacherAcademyRole.TEACHER);
        assertThatThrownBy(() -> progress.updateIncorrectCount(student.getId(), homework.getId(), 0, 0, "", "", foreignRecord))
                .isInstanceOf(ForbiddenException.class);
        Clinic clinic = persist(Clinic.builder().academyClass(clazz).clinicDate(LocalDate.of(2026, 10, 23))
                .clinicTime(LocalTime.of(14, 0)).build());
        clinics.startClinic(clinic.getId());
        assertThat(snapshots.findByClinicId(clinic.getId()).stream().map(p -> p.getStudentHomework().getId()).toList())
                .containsExactlyInAnyOrder(record(first).getId(), record(second).getId());
        TenantContext.setStudent(student.getId(), academy.getId(), clazz.getId());
        assertThatThrownBy(() -> progress.updateQuestionedQuestions(student.getId(), homework.getId(), "1", foreignRecord))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void detachingLessonPreservesItsGradeSourceAndLeavesOtherLessonUntouched() {
        Long firstId = record(first).getId();
        progress.updateIncorrectCount(student.getId(), homework.getId(), 3, 0, "1,2,3", "", firstId);
        assignments.detach(first, homework);
        first.getSharedHomeworks().clear();
        em.flush();
        em.clear();
        StudentHomework retained = records.findById(firstId).orElseThrow();
        assertThat(retained.getIncorrectCount()).isEqualTo(3);
        assertThat(retained.getLesson().getId()).isEqualTo(first.getId());
        assertThat(assignments.getProgressByStudent(em.find(Lesson.class, first.getId()))).isEmpty();
        assertThat(assignments.getProgressByStudent(em.find(Lesson.class, second.getId())).get(student.getId()).getIncorrectCount()).isNull();
    }

    @Test
    void clinicSnapshotNeverSwitchesToAnotherLessonWhenItsOriginalRecordIsRemoved() {
        StudentHomework a = record(first);
        StudentHomework b = record(second);
        a.setIncorrectCount(4);
        b.setIncorrectCount(2);
        Clinic clinic = persist(Clinic.builder().academyClass(clazz).clinicDate(LocalDate.of(2026, 10, 22))
                .clinicTime(LocalTime.of(14, 0)).build());
        clinics.startClinic(clinic.getId());
        Long snapshotId = snapshots.findByClinicIdAndStudentHomeworkId(clinic.getId(), a.getId()).orElseThrow().getId();
        em.flush();
        em.createNativeQuery("DELETE FROM student_homeworks WHERE id = :id").setParameter("id", a.getId()).executeUpdate();
        em.clear();
        clinics.endClinic(clinic.getId());
        ClinicHomeworkProgress retained = snapshots.findById(snapshotId).orElseThrow();
        assertThat(retained.getStudentHomework()).isNull();
        assertThat(retained.getIncorrectCountBefore()).isEqualTo(4);
        assertThat(retained.getIncorrectCountAfter()).isNull();
    }

    private StudentHomework record(Lesson lesson) {
        return records.findByStudentIdAndHomeworkIdAndLessonId(student.getId(), homework.getId(), lesson.getId()).orElseThrow();
    }
    private Lesson lesson(AcademyClass member, int day) {
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(member).lessonDate(LocalDate.of(2026, 10, day)).build());
        lesson.getSharedHomeworks().add(homework);
        homework.getSharedLessons().add(lesson);
        return lesson;
    }
    private <T> T persist(T value) { em.persist(value); return value; }
}
