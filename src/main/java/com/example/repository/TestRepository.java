package com.example.repository;

import com.example.entity.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestRepository extends JpaRepository<Test, Long> {
    @Query("SELECT t FROM Test t WHERE t.academy.id = :academyId " +
           "AND t.academyClass.id = :classId AND t.lesson IS NULL")
    List<Test> findUnattachedByAcademyIdAndClassId(
            @Param("academyId") Long academyId,
            @Param("classId") Long classId);

    List<Test> findAllByOrderByCreatedAtDesc();
}