package com.example.repository;

import com.example.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findByStudentId(Long studentId);
    List<DeviceToken> findByStudentIdIn(List<Long> studentIds);
    long deleteByToken(String token);
}
