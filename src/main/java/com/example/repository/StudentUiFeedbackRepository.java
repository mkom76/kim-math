package com.example.repository;

import com.example.entity.StudentUiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface StudentUiFeedbackRepository extends JpaRepository<StudentUiFeedback, Long> {
    long countByStudentIdAndCreatedAtAfter(Long studentId, LocalDateTime createdAt);
}
