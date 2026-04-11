package com.example.repository;

import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherAcademyRepository extends JpaRepository<TeacherAcademy, Long> {
    List<TeacherAcademy> findByTeacherId(Long teacherId);
    List<TeacherAcademy> findByAcademyId(Long academyId);
    Optional<TeacherAcademy> findByTeacherIdAndAcademyId(Long teacherId, Long academyId);
    boolean existsByTeacherIdAndAcademyIdAndRole(Long teacherId, Long academyId, TeacherAcademyRole role);
    void deleteByTeacherIdAndAcademyId(Long teacherId, Long academyId);
}
