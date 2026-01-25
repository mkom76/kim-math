package com.example.repository;

import com.example.entity.Homework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    @Query("SELECT h FROM Homework h WHERE h.academy.id = :academyId " +
           "AND h.academyClass.id = :classId AND h.lesson IS NULL")
    List<Homework> findUnattachedByAcademyIdAndClassId(
            @Param("academyId") Long academyId,
            @Param("classId") Long classId);

    List<Homework> findAllByOrderByCreatedAtDesc();
}
