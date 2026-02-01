package com.example.repository;

import com.example.entity.LessonVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonVideoRepository extends JpaRepository<LessonVideo, Long> {
    List<LessonVideo> findByLessonIdOrderByOrderIndexAsc(Long lessonId);
}
