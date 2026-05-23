package com.example.repository;

import com.example.entity.StudentConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentConsentRepository extends JpaRepository<StudentConsent, Long> {
    Optional<StudentConsent> findByToken(String token);
    List<StudentConsent> findByStudentIdOrderByIdDesc(Long studentId);
}
