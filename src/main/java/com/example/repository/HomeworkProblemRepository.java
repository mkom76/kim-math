package com.example.repository;

import com.example.entity.HomeworkProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HomeworkProblemRepository extends JpaRepository<HomeworkProblem, Long> {
    List<HomeworkProblem> findByHomeworkIdOrderByPositionAsc(Long homeworkId);
    void deleteByHomeworkId(Long homeworkId);
}
