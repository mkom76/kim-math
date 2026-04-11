package com.example.repository;

import com.example.entity.StudentHomework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentHomeworkRepository extends JpaRepository<StudentHomework, Long> {
    List<StudentHomework> findByStudentId(Long studentId);
    List<StudentHomework> findByHomeworkIdIn(List<Long> homeworkIds);
    Optional<StudentHomework> findByStudentIdAndHomeworkId(Long studentId, Long homeworkId);
    List<StudentHomework> findByStudentIdAndFollowUpFlagTrue(Long studentId);
}
