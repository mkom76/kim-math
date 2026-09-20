package com.example.repository;

import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentClassEnrollmentRepository extends JpaRepository<StudentClassEnrollment, Long> {
    @EntityGraph(attributePaths = {"academyClass", "academyClass.academy"})
    List<StudentClassEnrollment> findByStudentIdOrderByStartedAtDescIdDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "academyClass", "academyClass.academy"})
    List<StudentClassEnrollment> findByStudentIdInOrderByStartedAtDescIdDesc(List<Long> studentIds);

    @EntityGraph(attributePaths = {"academyClass", "academyClass.academy"})
    Optional<StudentClassEnrollment> findByStudentIdAndAcademyClassId(Long studentId, Long classId);

    @EntityGraph(attributePaths = {"student", "academyClass", "academyClass.academy"})
    List<StudentClassEnrollment> findByAcademyClassIdAndStatus(
            Long classId,
            StudentClassEnrollmentStatus status);

    boolean existsByStudentId(Long studentId);
    boolean existsByAcademyClassId(Long classId);
}
