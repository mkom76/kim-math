package com.example.repository;

import com.example.entity.StudentVideoProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentVideoProgressRepository extends JpaRepository<StudentVideoProgress, Long> {
    Optional<StudentVideoProgress> findByStudentIdAndLessonVideoId(Long studentId, Long lessonVideoId);
    List<StudentVideoProgress> findByStudentId(Long studentId);
    List<StudentVideoProgress> findByLessonVideoId(Long lessonVideoId);
}
