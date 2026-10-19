package com.example.repository;

import com.example.config.JpaAuditingConfig;
import com.example.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class StudentSubmissionScoreHistoryTest {
    @Autowired private StudentSubmissionRepository repository;
    @Autowired private TestEntityManager em;

    private Academy academy;
    private AcademyClass academyClass;
    private Student student;
    private final LocalDate targetDate = LocalDate.of(2026, 10, 20);

    @BeforeEach
    void setUp() {
        academy = em.persist(Academy.builder().name("학원").build());
        academyClass = em.persist(AcademyClass.builder().academy(academy).name("반").build());
        student = newStudent("학생");
    }

    @Test
    void returnsOnlySameStudentClassAcademyPastGradedResultsAndLimitsAfterFiltering() {
        StudentSubmission oldest = submission(student, academyClass, targetDate.minusDays(5), 60);
        StudentSubmission older = submission(student, academyClass, targetDate.minusDays(4), 70);
        StudentSubmission latest = submission(student, academyClass, targetDate.minusDays(3), 80);
        // Submission timestamps deliberately disagree with lesson order.
        oldest.setSubmittedAt(LocalDateTime.of(2026, 10, 25, 10, 0));
        submission(student, academyClass, targetDate, 90);
        submission(student, academyClass, targetDate.plusDays(1), 95);
        submission(student, academyClass, targetDate.minusDays(1), null);
        StudentSubmission pending = submission(student, academyClass, targetDate.minusDays(2), 50);
        var essay = em.persist(TestQuestion.builder().test(pending.getTest()).number(1).points(10.0)
                .questionType(QuestionType.ESSAY).build());
        em.persist(StudentSubmissionDetail.builder().submission(pending).question(essay).earnedPoints(null).build());
        em.persist(StudentSubmission.builder().student(newStudent("다른 학생"))
                .test(latest.getTest()).totalScore(100).build());
        var otherClass = em.persist(AcademyClass.builder().academy(academy).name("다른 반").build());
        submission(student, otherClass, targetDate.minusDays(1), 100);
        var otherAcademy = em.persist(Academy.builder().name("다른 학원").build());
        var foreignClass = em.persist(AcademyClass.builder().academy(otherAcademy).name("외부 반").build());
        submission(student, foreignClass, targetDate.minusDays(1), 100);
        em.flush();
        em.clear();

        assertThat(repository.findRecentGradedBeforeDate(student.getId(), academy.getId(), academyClass.getId(),
                targetDate, PageRequest.of(0, 2)))
                .extracting(StudentSubmission::getId).containsExactly(latest.getId(), older.getId());
        assertThat(repository.findRecentGradedBeforeDate(student.getId(), otherAcademy.getId(), academyClass.getId(),
                targetDate, PageRequest.of(0, 3))).isEmpty();
    }

    @Test
    void includesGradedEssaysAndAggregatesClassAveragesForRequestedTests() {
        StudentSubmission graded = submission(student, academyClass, targetDate.minusDays(1), 80);
        var essay = em.persist(TestQuestion.builder().test(graded.getTest()).number(1).points(10.0)
                .questionType(QuestionType.ESSAY).build());
        em.persist(StudentSubmissionDetail.builder().submission(graded).question(essay).earnedPoints(0.0).build());
        em.persist(StudentSubmission.builder().student(newStudent("다른 학생"))
                .test(graded.getTest()).totalScore(60).build());
        submission(student, academyClass, targetDate, 100);
        em.flush();
        em.clear();

        assertThat(repository.findRecentGradedBeforeDate(student.getId(), academy.getId(), academyClass.getId(),
                targetDate, PageRequest.of(0, 10)))
                .extracting(StudentSubmission::getId).containsExactly(graded.getId());
        assertThat(repository.getAverageScoresByTestIds(List.of(graded.getTest().getId())))
                .singleElement().satisfies(row -> {
                    assertThat(row[0]).isEqualTo(graded.getTest().getId());
                    assertThat(row[1]).isEqualTo(70.0);
                });
    }

    private Student newStudent(String name) {
        return em.persist(Student.builder().name(name).grade("중3").school("학교")
                .academy(academy).academyClass(academyClass).build());
    }

    private StudentSubmission submission(Student target, AcademyClass clazz, LocalDate date, Integer score) {
        var lesson = em.persist(Lesson.builder().academy(clazz.getAcademy()).academyClass(clazz).lessonDate(date).build());
        var test = em.persist(com.example.entity.Test.builder().academy(clazz.getAcademy()).academyClass(clazz)
                .lesson(lesson).title("시험 " + date).build());
        return em.persist(StudentSubmission.builder().student(target).test(test).totalScore(score).build());
    }
}
