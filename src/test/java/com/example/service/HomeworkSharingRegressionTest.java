package com.example.service;

import com.example.config.security.TenantContext;
import com.example.entity.*;
import com.example.exception.ForbiddenException;
import com.example.repository.HomeworkProblemRepository;
import com.example.repository.StudentHomeworkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeworkSharingRegressionTest {
    @Autowired EntityManager em;
    @Autowired HomeworkService homeworkService;
    @Autowired HomeworkProblemRepository problems;
    @Autowired StudentHomeworkRepository progressRepository;
    @Autowired LessonHomeworkAssignmentService assignments;
    @Autowired StudentHomeworkService progressService;

    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void sharedTeacherCanKeepAndReorderExistingTextbookProblems() {
        Academy academy = persist(Academy.builder().name("공유 숙제 학원").build());
        AcademyClass source = clazz(academy, "관리 반", 100L);
        AcademyClass target = clazz(academy, "공유받는 반", 200L);
        Homework homework = persist(Homework.builder().title("공통 숙제").questionCount(2)
                .academy(academy).academyClass(source).build());
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(target)
                .lessonDate(LocalDate.of(2026, 10, 20)).build());
        lesson.getSharedHomeworks().add(homework);
        homework.getSharedLessons().add(lesson);
        Textbook textbook = persist(Textbook.builder().title("관리 교사의 교재").ownerTeacherId(100L).build());
        TextbookProblem first = problem(textbook, 1);
        TextbookProblem second = problem(textbook, 2);
        persist(HomeworkProblem.builder().homework(homework).textbookProblem(first).position(1).build());
        persist(HomeworkProblem.builder().homework(homework).textbookProblem(second).position(2).build());
        em.flush();
        TenantContext.set(200L, academy.getId(), TeacherAcademyRole.TEACHER);

        homeworkService.replaceProblems(homework.getId(), List.of(
                new HomeworkService.ReplaceProblemItem(second.getId()),
                new HomeworkService.ReplaceProblemItem(null),
                new HomeworkService.ReplaceProblemItem(first.getId())));
        em.flush();
        em.clear();

        assertThat(problems.findByHomeworkIdOrderByPositionAsc(homework.getId()))
                .extracting(row -> row.getTextbookProblem() == null ? null : row.getTextbookProblem().getId())
                .containsExactly(second.getId(), null, first.getId());
        assertThat(em.find(Homework.class, homework.getId()).getQuestionCount()).isEqualTo(3);
    }

    @Test
    void sharedTeacherCannotAddUnrelatedPrivateTextbookProblemAndExistingRowsRemain() {
        Academy academy = persist(Academy.builder().name("개인 교재 경계").build());
        AcademyClass source = clazz(academy, "공유 반", 100L);
        AcademyClass target = clazz(academy, "내 반", 200L);
        Homework homework = persist(Homework.builder().title("공통 숙제").questionCount(1)
                .academy(academy).academyClass(source).build());
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(target)
                .lessonDate(LocalDate.of(2026, 10, 20)).build());
        lesson.getSharedHomeworks().add(homework);
        homework.getSharedLessons().add(lesson);
        Textbook textbook = persist(Textbook.builder().title("타인의 개인 교재").ownerTeacherId(100L).build());
        TextbookProblem retained = problem(textbook, 1);
        TextbookProblem unrelated = problem(textbook, 2);
        persist(HomeworkProblem.builder().homework(homework).textbookProblem(retained).position(1).build());
        em.flush();
        TenantContext.set(200L, academy.getId(), TeacherAcademyRole.TEACHER);

        assertThatThrownBy(() -> homeworkService.replaceProblems(homework.getId(), List.of(
                new HomeworkService.ReplaceProblemItem(unrelated.getId()))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(problems.findByHomeworkIdOrderByPositionAsc(homework.getId()))
                .extracting(row -> row.getTextbookProblem().getId()).containsExactly(retained.getId());
    }

    @Test
    void detachedSharedHomeworkRemainsVisibleAndEditableInItsHistoricalClass() {
        Academy academy = persist(Academy.builder().name("공유 이력 학원").build());
        AcademyClass source = clazz(academy, "관리 반", 100L);
        AcademyClass target = clazz(academy, "배정받은 반", 200L);
        Student student = student(academy, target, "학생");
        Homework homework = persist(Homework.builder().title("공유 숙제").questionCount(10)
                .academy(academy).academyClass(source).build());
        Lesson lesson = sharedLesson(academy, target, homework);
        assignments.assign(lesson, Map.of(student.getId(), homework.getId()));
        StudentHomework progress = progressRepository.findByStudentIdAndHomeworkId(student.getId(), homework.getId()).orElseThrow();
        progress.setIncorrectCount(2);
        assignments.detach(lesson, homework);
        lesson.getSharedHomeworks().clear();
        homework.getSharedLessons().clear();
        em.flush();
        em.clear();
        TenantContext.set(200L, academy.getId(), TeacherAcademyRole.TEACHER);

        assertThat(progressService.getByStudentId(student.getId(), target.getId()))
                .extracting(row -> row.getHomeworkId()).containsExactly(homework.getId());
        assertThat(progressService.updateIncorrectCount(student.getId(), homework.getId(), 1, 0, "2", "").getCompletion())
                .isEqualTo(90);
        assertThat(progressRepository.findByStudentIdAndHomeworkAcademyClassId(student.getId(), source.getId())).isEmpty();
    }

    @Test
    void contentSharingDoesNotGrantAccessToAnExcludedStudentsOtherClassProgress() {
        Academy academy = persist(Academy.builder().name("학생별 기록 경계").build());
        AcademyClass mine = clazz(academy, "내 반", 100L);
        AcademyClass other = clazz(academy, "다른 반", 200L);
        Student excluded = student(academy, mine, "내 반에서는 미배정");
        Student assigned = student(academy, mine, "내 반 배정 학생");
        enroll(excluded, mine);
        enroll(excluded, other);
        Homework homework = persist(Homework.builder().title("다른 교사의 공유 숙제").questionCount(10)
                .academy(academy).academyClass(other).build());
        Lesson mineLesson = sharedLesson(academy, mine, homework);
        Lesson otherLesson = sharedLesson(academy, other, homework);
        assignments.assign(otherLesson, Map.of(excluded.getId(), homework.getId()));
        assignments.assign(mineLesson, Map.of(assigned.getId(), homework.getId()));
        StudentHomework hidden = progressRepository.findByStudentIdAndHomeworkId(excluded.getId(), homework.getId()).orElseThrow();
        hidden.setIncorrectCount(3);
        hidden.setFollowUpFlag(true);
        em.flush();
        em.clear();
        TenantContext.set(100L, academy.getId(), TeacherAcademyRole.TEACHER);

        assertThat(progressService.getByStudentId(excluded.getId(), null)).isEmpty();
        assertThat(progressService.getFollowUpsByStudent(excluded.getId())).isEmpty();
        assertThatThrownBy(() -> progressService.updateIncorrectCount(excluded.getId(), homework.getId(), 0, 0, "", ""))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> progressService.setFollowUp(excluded.getId(), homework.getId(), false))
                .isInstanceOf(ForbiddenException.class);

        TenantContext.setStudent(excluded.getId(), academy.getId(), mine.getId());
        assertThat(progressService.getByStudentId(excluded.getId(), mine.getId())).isEmpty();
        assertThatThrownBy(() -> progressService.updateQuestionedQuestions(excluded.getId(), homework.getId(), "1"))
                .isInstanceOf(ForbiddenException.class);
        TenantContext.setStudent(excluded.getId(), academy.getId(), other.getId());
        assertThat(progressService.updateQuestionedQuestions(excluded.getId(), homework.getId(), "1").getQuestionedQuestions()).isEqualTo("1");
    }

    @Test
    void classHistoryDoesNotBlockPhysicalPurgingOfLearningRecords() {
        Academy academy = persist(Academy.builder().name("이력 파기 경계").build());
        AcademyClass clazz = clazz(academy, "반", 100L);
        Student student = student(academy, clazz, "학생");
        Homework homework = persist(Homework.builder().title("숙제").questionCount(10).academy(academy).academyClass(clazz).build());
        Lesson lesson = sharedLesson(academy, clazz, homework);
        assignments.assign(lesson, Map.of(student.getId(), homework.getId()));
        em.flush();
        Long recordId = progressRepository.findByStudentIdAndHomeworkId(student.getId(), homework.getId()).orElseThrow().getId();
        em.createNativeQuery("DELETE FROM student_homeworks WHERE student_id = :id").setParameter("id", student.getId()).executeUpdate();
        em.clear();
        assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM student_homework_classes WHERE student_homework_id = :id")
                .setParameter("id", recordId).getSingleResult()).longValue()).isZero();
    }

    private Lesson sharedLesson(Academy academy, AcademyClass clazz, Homework homework) {
        Lesson lesson = persist(Lesson.builder().academy(academy).academyClass(clazz)
                .lessonDate(LocalDate.of(2026, 10, 20)).build());
        lesson.getSharedHomeworks().add(homework);
        homework.getSharedLessons().add(lesson);
        return lesson;
    }
    private Student student(Academy academy, AcademyClass clazz, String name) {
        return persist(Student.builder().name(name).grade("고1").school("학교").pin("1111").academy(academy).academyClass(clazz).build());
    }
    private void enroll(Student student, AcademyClass clazz) {
        persist(StudentClassEnrollment.builder().student(student).academyClass(clazz)
                .status(StudentClassEnrollmentStatus.ACTIVE).startedAt(java.time.LocalDateTime.now()).build());
    }

    private AcademyClass clazz(Academy academy, String name, Long owner) {
        return persist(AcademyClass.builder().name(name).academy(academy).ownerTeacherId(owner).build());
    }
    private TextbookProblem problem(Textbook textbook, int number) {
        return persist(TextbookProblem.builder().textbook(textbook).number(number).topic("수학").build());
    }
    private <T> T persist(T value) { em.persist(value); return value; }
}
