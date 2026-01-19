package com.example.repository;

import com.example.entity.ClinicHomeworkProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicHomeworkProgressRepository extends JpaRepository<ClinicHomeworkProgress, Long> {

    // 특정 클리닉의 모든 진행 상황 조회
    List<ClinicHomeworkProgress> findByClinicId(Long clinicId);

    // 특정 클리닉의 특정 학생의 진행 상황 조회
    List<ClinicHomeworkProgress> findByClinicIdAndStudentId(Long clinicId, Long studentId);

    // 특정 조합 조회
    Optional<ClinicHomeworkProgress> findByClinicIdAndStudentIdAndHomeworkId(Long clinicId, Long studentId, Long homeworkId);

    // 클리닉 삭제 시 자동 삭제 (CASCADE)
    void deleteByClinicId(Long clinicId);
}
