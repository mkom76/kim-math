package com.example.repository;

import com.example.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, Long> {
    Optional<RememberMeToken> findBySelectorHash(String selectorHash);

    List<RememberMeToken> findByUserRoleAndUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(
            String userRole, Long userId);

    @Modifying
    @Query("delete from RememberMeToken t where t.expiresAt <= :now or t.revokedAt is not null")
    int deleteExpiredOrRevoked(@Param("now") LocalDateTime now);
}
