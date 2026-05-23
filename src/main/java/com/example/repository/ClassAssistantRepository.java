package com.example.repository;

import com.example.entity.ClassAssistant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassAssistantRepository extends JpaRepository<ClassAssistant, ClassAssistant.PK> {
    List<ClassAssistant> findByClassId(Long classId);
    List<ClassAssistant> findByTeacherId(Long teacherId);
    List<ClassAssistant> findByClassIdIn(List<Long> classIds);
    boolean existsByClassIdAndTeacherId(Long classId, Long teacherId);
    long deleteByClassIdAndTeacherId(Long classId, Long teacherId);
    long deleteByTeacherId(Long teacherId);

    /**
     * Remove this teacher's assistant assignments scoped to the given academy
     * only. Used when a teacher is removed from one academy but remains a
     * member of others.
     */
    @Modifying
    @Query("DELETE FROM ClassAssistant ca WHERE ca.teacherId = :teacherId AND ca.classId IN " +
           "(SELECT c.id FROM AcademyClass c WHERE c.academy.id = :academyId)")
    int deleteByTeacherIdInAcademy(@Param("teacherId") Long teacherId,
                                   @Param("academyId") Long academyId);
}
