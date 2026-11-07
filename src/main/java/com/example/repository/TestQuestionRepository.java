package com.example.repository;

import com.example.entity.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    List<TestQuestion> findByTestIdOrderByNumber(Long testId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT q FROM TestQuestion q WHERE q.test.id = :testId ORDER BY q.number")
    List<TestQuestion> findByTestIdForSnapshot(@Param("testId") Long testId);
    void deleteByTestId(Long testId);
}
