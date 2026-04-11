package com.example.repository;

import com.example.entity.AcademyClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademyClassRepository extends JpaRepository<AcademyClass, Long> {
    List<AcademyClass> findByAcademyId(Long academyId);

    int countByAcademy_IdAndOwnerTeacherId(Long academyId, Long ownerTeacherId);

    @Modifying
    @Query("UPDATE AcademyClass c SET c.ownerTeacherId = :newOwner " +
           "WHERE c.academy.id = :academyId AND c.ownerTeacherId = :oldOwner")
    int transferOwnership(@Param("academyId") Long academyId,
                          @Param("oldOwner") Long oldOwner,
                          @Param("newOwner") Long newOwner);
}
