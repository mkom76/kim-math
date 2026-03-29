package com.example.repository;

import com.example.entity.StudentLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentLessonRepository extends JpaRepository<StudentLesson, Long> {
    Optional<StudentLesson> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    List<StudentLesson> findByStudentIdOrderByLesson_LessonDateDesc(Long studentId);

    List<StudentLesson> findByLessonId(Long lessonId);

    @Query("SELECT sl FROM StudentLesson sl WHERE sl.student.id = :studentId " +
           "AND sl.lesson.lessonDate = :lessonDate")
    Optional<StudentLesson> findByStudentIdAndDate(@Param("studentId") Long studentId,
                                                     @Param("lessonDate") LocalDate lessonDate);

    @Query("SELECT sl FROM StudentLesson sl " +
           "WHERE sl.feedbackAuthor = (SELECT t.name FROM Teacher t WHERE t.id = :teacherId) " +
           "AND sl.instructorFeedback IS NOT NULL " +
           "AND sl.instructorFeedback <> '' " +
           "AND (sl.isAiFeedback = false OR sl.isAiFeedback IS NULL) " +
           "AND sl.id IN (" +
           "  SELECT MAX(sl2.id) FROM StudentLesson sl2 " +
           "  WHERE sl2.feedbackAuthor = (SELECT t2.name FROM Teacher t2 WHERE t2.id = :teacherId) " +
           "  AND sl2.instructorFeedback IS NOT NULL " +
           "  AND sl2.instructorFeedback <> '' " +
           "  AND (sl2.isAiFeedback = false OR sl2.isAiFeedback IS NULL) " +
           "  GROUP BY sl2.lesson.id" +
           ") " +
           "ORDER BY sl.updatedAt DESC " +
           "LIMIT :limit")
    List<StudentLesson> findRecentByFeedbackAuthorTeacherId(
            @Param("teacherId") Long teacherId,
            @Param("limit") int limit);
}
