package com.example.service;

import com.example.config.security.TenantContext;
import com.example.entity.*;
import com.example.repository.StudentHomeworkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HomeworkSharingConcurrencyTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired LessonService lessons;
    @Autowired StudentHomeworkRepository records;
    private final java.util.List<Fixture> fixtures = new java.util.ArrayList<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        TenantContext.clear();
        for (Fixture fixture : fixtures) tx().executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM student_lessons WHERE student_id = :id").setParameter("id", fixture.student()).executeUpdate();
            em.createNativeQuery("DELETE FROM student_homeworks WHERE student_id = :id").setParameter("id", fixture.student()).executeUpdate();
            em.createNativeQuery("DELETE FROM lesson_homeworks WHERE homework_id = :id").setParameter("id", fixture.homework()).executeUpdate();
            em.createNativeQuery("DELETE FROM homeworks WHERE id = :id").setParameter("id", fixture.homework()).executeUpdate();
            em.createNativeQuery("DELETE FROM lessons WHERE academy_id = :id").setParameter("id", fixture.academy()).executeUpdate();
            em.createNativeQuery("DELETE FROM student_class_enrollments WHERE student_id = :id").setParameter("id", fixture.student()).executeUpdate();
            em.createNativeQuery("DELETE FROM students WHERE id = :id").setParameter("id", fixture.student()).executeUpdate();
            em.createNativeQuery("DELETE FROM academy_classes WHERE academy_id = :id").setParameter("id", fixture.academy()).executeUpdate();
            em.createNativeQuery("DELETE FROM academies WHERE id = :id").setParameter("id", fixture.academy()).executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentAssignmentsCreateOneRecordPerLesson(boolean sameLesson) throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> withContext(fixture, () -> tx().executeWithoutResult(status -> {
                lessons.assignHomeworksToStudents(fixture.firstLesson(), Map.of(fixture.student(), fixture.homework()));
                em.flush();
                firstWritten.countDown();
                await(releaseFirst);
            })));
            assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> withContext(fixture, () -> {
                secondStarted.countDown();
                lessons.assignHomeworksToStudents(sameLesson ? fixture.firstLesson() : fixture.secondLesson(), Map.of(fixture.student(), fixture.homework()));
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            tx().executeWithoutResult(status -> {
                List<StudentHomework> progress = records.findByStudentId(fixture.student());
                assertThat(progress.stream().map(StudentHomework::getId).toList()).hasSize(sameLesson ? 1 : 2);
                assertThat(records.findByStudentIdAndHomeworkIdAndLessonId(fixture.student(), fixture.homework(), fixture.firstLesson()).orElseThrow()
                        .getClassHistory().stream().map(AcademyClass::getId).toList()).containsExactly(fixture.firstClass());
                if (!sameLesson) assertThat(records.findByStudentIdAndHomeworkIdAndLessonId(fixture.student(), fixture.homework(), fixture.secondLesson()).orElseThrow()
                        .getClassHistory().stream().map(AcademyClass::getId).toList()).containsExactly(fixture.secondClass());
                assertThat(em.find(Lesson.class, fixture.firstLesson()).getHomeworks()).extracting(Homework::getId).contains(fixture.homework());
                assertThat(em.find(Lesson.class, fixture.secondLesson()).getHomeworks()).extracting(Homework::getId).contains(fixture.homework());
            });
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void databaseRejectsDuplicateStudentHomeworkInSameLessonIncludingLegacyUnattached(boolean withLesson) {
        Fixture fixture = fixture();
        tx().executeWithoutResult(status -> records.save(StudentHomework.builder()
                .student(em.find(Student.class, fixture.student())).homework(em.find(Homework.class, fixture.homework()))
                .lesson(withLesson ? em.find(Lesson.class, fixture.firstLesson()) : null).build()));
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> records.saveAndFlush(StudentHomework.builder()
                .student(em.find(Student.class, fixture.student())).homework(em.find(Homework.class, fixture.homework()))
                .lesson(withLesson ? em.find(Lesson.class, fixture.firstLesson()) : null).build())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        Fixture fixture = tx().execute(status -> {
            Academy academy = persist(Academy.builder().name("동시 배정 검증").build());
            AcademyClass first = persist(AcademyClass.builder().name("기본반").academy(academy).ownerTeacherId(100L).build());
            AcademyClass second = persist(AcademyClass.builder().name("심화반").academy(academy).ownerTeacherId(100L).build());
            Student student = persist(Student.builder().name("공유 학생").grade("고1").school("학교").pin("1111")
                    .academy(academy).academyClass(first).build());
            for (AcademyClass clazz : List.of(first, second)) {
                persist(StudentClassEnrollment.builder().student(student).academyClass(clazz)
                        .status(StudentClassEnrollmentStatus.ACTIVE).startedAt(LocalDateTime.now()).build());
            }
            Homework homework = persist(Homework.builder().title("공통 숙제").questionCount(10).academy(academy).academyClass(first).build());
            Lesson a = persist(Lesson.builder().academy(academy).academyClass(first).lessonDate(LocalDate.of(2026, 10, 20)).build());
            Lesson b = persist(Lesson.builder().academy(academy).academyClass(second).lessonDate(LocalDate.of(2026, 10, 20)).build());
            a.getSharedHomeworks().add(homework);
            b.getSharedHomeworks().add(homework);
            return new Fixture(academy.getId(), student.getId(), homework.getId(), first.getId(), second.getId(), a.getId(), b.getId());
        });
        fixtures.add(fixture);
        return fixture;
    }
    private TransactionTemplate tx() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }
    private void withContext(Fixture fixture, Runnable work) {
        TenantContext.set(100L, fixture.academy(), TeacherAcademyRole.ACADEMY_ADMIN);
        try { work.run(); } finally { TenantContext.clear(); }
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("동시 배정 검증 시간이 초과되었습니다");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
    private <T> T persist(T value) { em.persist(value); return value; }
    private record Fixture(Long academy, Long student, Long homework, Long firstClass, Long secondClass,
                           Long firstLesson, Long secondLesson) {}
}
