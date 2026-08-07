package com.example.service;

import com.example.entity.Student;
import com.example.entity.StudentConsent;
import com.example.repository.StudentConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class StudentConsentIssuer {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int DEFAULT_TOKEN_TTL_DAYS = 14;

    private final StudentConsentRepository studentConsentRepository;

    @Value("${app.consent.version:v1}")
    private String currentConsentVersion;

    public String issue(Student student, LocalDateTime issuedAt) {
        String token = generateToken();
        StudentConsent consent = StudentConsent.builder()
                .studentId(student.getId())
                .consentVersion(currentConsentVersion)
                .token(token)
                .tokenIssuedAt(issuedAt)
                .tokenExpiresAt(issuedAt.plusDays(DEFAULT_TOKEN_TTL_DAYS))
                .build();
        studentConsentRepository.save(consent);
        return token;
    }

    private static String generateToken() {
        byte[] buf = new byte[TOKEN_BYTE_LENGTH];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
