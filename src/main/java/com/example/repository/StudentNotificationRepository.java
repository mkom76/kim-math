package com.example.repository;

import com.example.entity.StudentNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudentNotificationRepository extends JpaRepository<StudentNotification, Long> {
    List<StudentNotification> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    long countByStudentIdAndReadAtIsNull(Long studentId);

    boolean existsByStudentIdAndSourceKey(Long studentId, String sourceKey);

    @Modifying
    @Query("update StudentNotification n set n.readAt = :readAt " +
            "where n.studentId = :studentId and n.readAt is null")
    int markAllRead(@Param("studentId") Long studentId, @Param("readAt") LocalDateTime readAt);
}
