package com.example.repository;

import com.example.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, Long> {
    Optional<RememberMeToken> findBySelectorHash(String selectorHash);
}
