package com.example.repository;

import com.example.entity.StudentHomework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentHomeworkRepository extends JpaRepository<StudentHomework, Long> {
    List<StudentHomework> findByStudentId(Long studentId);
    boolean existsByLessonId(Long lessonId);
    @Query("SELECT sh FROM StudentHomework sh WHERE sh.student.id = :studentId " +
            "AND EXISTS (SELECT c.id FROM sh.classHistory c WHERE c.id = :classId)")
    List<StudentHomework> findByStudentIdAndHomeworkAcademyClassId(Long studentId, Long classId);
    List<StudentHomework> findByLessonIdAndHomeworkIdIn(Long lessonId, List<Long> homeworkIds);
    Optional<StudentHomework> findByStudentIdAndHomeworkId(Long studentId, Long homeworkId);
    Optional<StudentHomework> findByStudentIdAndHomeworkIdAndLessonId(Long studentId, Long homeworkId, Long lessonId);
    List<StudentHomework> findByStudentIdAndFollowUpFlagTrue(Long studentId);

    // Call only after authorizing/locking the student. Do not let entity filters hide an ambiguous request.
    @Query(value = "SELECT id FROM student_homeworks WHERE student_id = :studentId AND homework_id = :homeworkId ORDER BY id", nativeQuery = true)
    List<Long> findRecordIds(Long studentId, Long homeworkId);
}
