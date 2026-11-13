package com.example.repository;

import com.example.entity.HomeworkPdfImport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface HomeworkPdfImportRepository extends JpaRepository<HomeworkPdfImport, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HomeworkPdfImport i where i.id = :id")
    Optional<HomeworkPdfImport> findLocked(@Param("id") String id);
    Optional<HomeworkPdfImport> findByTeacherIdAndRequestKey(Long teacherId, String requestKey);
    List<HomeworkPdfImport> findByHomeworkIdIn(Collection<Long> ids);
    boolean existsByHomeworkId(Long homeworkId);
    long countByTeacherIdAndCreatedAtAfter(Long teacherId, LocalDateTime after);
    long countByCreatedAtAfter(LocalDateTime after);
    long countByState(String state);
    List<HomeworkPdfImport> findTop20ByStateInAndExpiresAtBefore(Collection<String> states, LocalDateTime before);
}
