package com.example.repository;

import com.example.entity.FeedbackPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackPromptTemplateRepository extends JpaRepository<FeedbackPromptTemplate, Long> {
    Optional<FeedbackPromptTemplate> findByTeacherId(Long teacherId);
}