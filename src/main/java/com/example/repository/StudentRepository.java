package com.example.repository;

import com.example.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Page<Student> findByNameContaining(String name, Pageable pageable);
    Optional<Student> findByIdAndPin(Long id, String pin);
    List<Student> findAllByOrderByCreatedAtDesc();
}