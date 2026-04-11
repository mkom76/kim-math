package com.example.repository;

import com.example.entity.Academy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface AcademyRepository extends JpaRepository<Academy, Long> {
    Page<Academy> findByIdIn(Collection<Long> ids, Pageable pageable);
}
