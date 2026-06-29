package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentBulkCreateRequest;
import com.example.dto.StudentBulkCreateResponse;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentConsent;
import com.example.entity.StudentStatus;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.repository.AcademyRepository;
import com.example.repository.StudentConsentRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentBulkService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32; // → 43 base64url chars, fits VARCHAR(64)
    private static final int DEFAULT_TOKEN_TTL_DAYS = 14;

    private final StudentRepository studentRepository;
    private final StudentConsentRepository studentConsentRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final AuthorizationService authorizationService;
    private final PinCredentialService pinCredentialService;

    @Value("${app.consent.version:v1}")
    private String currentConsentVersion;

    public StudentBulkCreateResponse bulkCreate(StudentBulkCreateRequest req) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        authorizationService.assertNotAssistant();

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("학원을 찾을 수 없습니다"));

        AcademyClass clazz = academyClassRepository.findById(req.getClassId())
                .orElseThrow(() -> new RuntimeException("반을 찾을 수 없습니다"));
        authorizationService.assertCanModifyClass(clazz);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(DEFAULT_TOKEN_TTL_DAYS);

        List<StudentBulkCreateResponse.Item> out = new java.util.ArrayList<>(req.getStudents().size());

        for (StudentBulkCreateRequest.Item it : req.getStudents()) {
            String pin = last4Digits(it.getParentPhone());
            if (pin == null) {
                throw new IllegalArgumentException(
                        "부모 휴대폰에서 숫자 4자리를 추출할 수 없습니다: " + it.getName());
            }

            Student student = Student.builder()
                    .name(it.getName().trim())
                    .grade(it.getGrade().trim())
                    .school(it.getSchool().trim())
                    .parentName(it.getParentName().trim())
                    .parentPhone(normalizePhone(it.getParentPhone()))
                    .contactPhone(emptyToNull(normalizePhone(it.getContactPhone())))
                    .status(StudentStatus.PENDING_CONSENT)
                    .academy(academy)
                    .academyClass(clazz)
                    .build();
            pinCredentialService.setStudentPin(student, pin);
            student = studentRepository.save(student);

            String token = generateToken();
            StudentConsent consent = StudentConsent.builder()
                    .studentId(student.getId())
                    .consentVersion(currentConsentVersion)
                    .token(token)
                    .tokenIssuedAt(now)
                    .tokenExpiresAt(expires)
                    .build();
            studentConsentRepository.save(consent);

            out.add(StudentBulkCreateResponse.Item.builder()
                    .studentId(student.getId())
                    .name(student.getName())
                    .parentName(student.getParentName())
                    .parentPhone(student.getParentPhone())
                    .consentToken(token)
                    .build());
        }

        return StudentBulkCreateResponse.builder().created(out).build();
    }

    /** Strip everything but digits and return the trailing 4. Null if fewer than 4 digits. */
    public static String last4Digits(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
    }

    /** Normalize a phone string: keep only digits and dashes, collapse whitespace. */
    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("[^0-9-]", "");
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String generateToken() {
        byte[] buf = new byte[TOKEN_BYTE_LENGTH];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
