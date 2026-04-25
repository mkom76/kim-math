package com.example.repository;

import com.example.entity.TextbookProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextbookProblemRepository extends JpaRepository<TextbookProblem, Long> {
    List<TextbookProblem> findByTextbookIdOrderByNumberAsc(Long textbookId);
}
