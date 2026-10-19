package com.example.repository;

import com.example.entity.StudentSubmission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentSubmissionRepository extends JpaRepository<StudentSubmission, Long> {
    List<StudentSubmission> findByTestId(Long testId);
    List<StudentSubmission> findByStudentId(Long studentId);
    List<StudentSubmission> findByStudentIdAndTestAcademyClassId(Long studentId, Long classId);
    Optional<StudentSubmission> findByStudentIdAndTestId(Long studentId, Long testId);
    
    @Query("SELECT AVG(s.totalScore) FROM StudentSubmission s WHERE s.test.id = :testId")
    Double getAverageScoreByTestId(Long testId);

    @Query("SELECT COUNT(d) FROM StudentSubmissionDetail d " +
           "WHERE d.submission.id = :submissionId " +
           "AND d.question.questionType = com.example.entity.QuestionType.ESSAY " +
           "AND d.earnedPoints IS NULL")
    Long countPendingEssays(@Param("submissionId") Long submissionId);

    @Query("""
            SELECT s FROM StudentSubmission s
            JOIN FETCH s.test t
            JOIN FETCH t.lesson l
            WHERE s.student.id = :studentId
              AND t.academy.id = :academyId
              AND t.academyClass.id = :classId
              AND l.lessonDate < :beforeDate
              AND s.totalScore IS NOT NULL
              AND NOT EXISTS (
                  SELECT d.id FROM StudentSubmissionDetail d
                  WHERE d.submission = s
                    AND d.question.questionType = com.example.entity.QuestionType.ESSAY
                    AND d.earnedPoints IS NULL
              )
            ORDER BY l.lessonDate DESC, t.id DESC
            """)
    List<StudentSubmission> findRecentGradedBeforeDate(
            @Param("studentId") Long studentId,
            @Param("academyId") Long academyId,
            @Param("classId") Long classId,
            @Param("beforeDate") LocalDate beforeDate,
            Pageable pageable);

    @Query("SELECT s.test.id, AVG(s.totalScore) FROM StudentSubmission s " +
           "WHERE s.test.id IN :testIds GROUP BY s.test.id")
    List<Object[]> getAverageScoresByTestIds(@Param("testIds") List<Long> testIds);
}
