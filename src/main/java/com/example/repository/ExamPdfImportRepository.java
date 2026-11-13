package com.example.repository;

import com.example.entity.ExamPdfImport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface ExamPdfImportRepository extends JpaRepository<ExamPdfImport, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ExamPdfImport i where i.id = :id")
    Optional<ExamPdfImport> findLocked(@Param("id") String id);
    Optional<ExamPdfImport> findByTeacherIdAndRequestKey(Long teacherId, String requestKey);
    List<ExamPdfImport> findByTestIdIn(Collection<Long> ids);
    boolean existsByTestId(Long testId);
    long countByTeacherIdAndCreatedAtAfter(Long teacherId, LocalDateTime after);
    long countByCreatedAtAfter(LocalDateTime after);
    long countByState(String state);
    List<ExamPdfImport> findTop20ByStateInAndExpiresAtBefore(Collection<String> states, LocalDateTime before);
}
