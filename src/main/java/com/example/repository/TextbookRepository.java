package com.example.repository;

import com.example.entity.Textbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextbookRepository extends JpaRepository<Textbook, Long> {
    List<Textbook> findByOwnerTeacherIdOrderByCreatedAtDesc(Long ownerTeacherId);
}
