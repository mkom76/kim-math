package com.example.repository;

import com.example.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface
LessonRepository extends JpaRepository<Lesson, Long> {
    @Query("SELECT l FROM Lesson l WHERE l.academy.id = :academyId " +
           "AND l.academyClass.id = :classId AND l.lessonDate = :lessonDate")
    Optional<Lesson> findByAcademyIdAndClassIdAndLessonDate(
            @Param("academyId") Long academyId,
            @Param("classId") Long classId,
            @Param("lessonDate") LocalDate lessonDate);

    @Query("SELECT l FROM Lesson l WHERE l.academyClass.id = :classId " +
           "ORDER BY l.lessonDate DESC")
    List<Lesson> findByAcademyClassIdOrderByLessonDateDesc(@Param("classId") Long classId);

    @Query("SELECT l FROM Lesson l WHERE l.academyClass.id = :classId " +
           "AND l.lessonDate > :currentDate ORDER BY l.lessonDate ASC")
    List<Lesson> findNextLessonsAfter(@Param("classId") Long classId,
                                       @Param("currentDate") LocalDate currentDate);

    List<Lesson> findAllByOrderByCreatedAtDesc();
}
