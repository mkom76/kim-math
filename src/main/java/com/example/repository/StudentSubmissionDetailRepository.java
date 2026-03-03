package com.example.repository;

import com.example.entity.StudentSubmissionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentSubmissionDetailRepository extends JpaRepository<StudentSubmissionDetail, Long> {
    List<StudentSubmissionDetail> findBySubmissionId(Long submissionId);

    // 문제 번호, 정답률, 문제 유형 반환 (OBJECTIVE/SUBJECTIVE용)
    @Query("SELECT d.question.number, " +
           "COUNT(CASE WHEN d.isCorrect = true THEN 1 END) * 100.0 / COUNT(*), " +
           "d.question.questionType " +
           "FROM StudentSubmissionDetail d " +
           "WHERE d.submission.test.id = :testId " +
           "GROUP BY d.question.number, d.question.questionType " +
           "ORDER BY d.question.number")
    List<Object[]> getQuestionCorrectRatesByTestId(@Param("testId") Long testId);

    // ESSAY 문제 번호, 평균 획득률 반환 (채점 완료된 것만)
    @Query("SELECT d.question.number, AVG(d.earnedPoints * 100.0 / d.question.points) " +
           "FROM StudentSubmissionDetail d " +
           "WHERE d.submission.test.id = :testId " +
           "AND d.question.questionType = com.example.entity.QuestionType.ESSAY " +
           "AND d.earnedPoints IS NOT NULL " +
           "GROUP BY d.question.number " +
           "ORDER BY d.question.number")
    List<Object[]> getEssayAvgEarnedRatesByTestId(@Param("testId") Long testId);
}