package com.example.service;

import com.example.dto.ConsentAgreeRequest;
import com.example.dto.ConsentInfoResponse;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentConsent;
import com.example.entity.StudentStatus;
import com.example.exception.ForbiddenException;
import com.example.repository.StudentConsentRepository;
import com.example.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsentService {

    private final StudentRepository studentRepository;
    private final StudentConsentRepository studentConsentRepository;

    @Transactional(readOnly = true)
    public ConsentInfoResponse get(String token) {
        StudentConsent consent = studentConsentRepository.findByToken(token)
                .orElseThrow(() -> new ForbiddenException("유효하지 않은 동의 링크입니다"));

        Student student = studentRepository.findById(consent.getStudentId())
                .orElseThrow(() -> new ForbiddenException("학생 정보를 찾을 수 없습니다"));

        boolean expired = consent.getTokenExpiresAt() != null
                && consent.getTokenExpiresAt().isBefore(LocalDateTime.now());
        boolean alreadyConsented = consent.getConsentedAt() != null;

        AcademyClass clazz = student.getAcademyClass();
        return ConsentInfoResponse.builder()
                .consentVersion(consent.getConsentVersion())
                .studentName(student.getName())
                .parentName(student.getParentName())
                .parentPhoneMasked(maskPhone(student.getParentPhone()))
                .academyName(student.getAcademy() != null ? student.getAcademy().getName() : null)
                .className(clazz != null ? clazz.getName() : null)
                .alreadyConsented(alreadyConsented)
                .expired(expired)
                .build();
    }

    public void agree(String token, ConsentAgreeRequest req, HttpServletRequest http) {
        StudentConsent consent = studentConsentRepository.findByToken(token)
                .orElseThrow(() -> new ForbiddenException("유효하지 않은 동의 링크입니다"));

        if (consent.getConsentedAt() != null) {
            throw new ForbiddenException("이미 동의가 완료된 링크입니다");
        }
        if (consent.getTokenExpiresAt() != null
                && consent.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ForbiddenException("동의 링크가 만료되었습니다. 학원에 재발급을 요청하세요.");
        }

        Student student = studentRepository.findById(consent.getStudentId())
                .orElseThrow(() -> new ForbiddenException("학생 정보를 찾을 수 없습니다"));

        String expectedLast4 = StudentBulkService.last4Digits(student.getParentPhone());
        if (expectedLast4 == null || !expectedLast4.equals(req.getParentPhoneLast4())) {
            throw new ForbiddenException("휴대폰 뒤 4자리가 일치하지 않습니다");
        }

        LocalDateTime now = LocalDateTime.now();
        consent.setConsentedAt(now);
        consent.setSignerType("PARENT");
        consent.setSignerPhoneLast4(req.getParentPhoneLast4());
        consent.setSignerIp(clientIp(http));
        consent.setSignerUserAgent(truncate(http.getHeader("User-Agent"), 255));
        // Invalidate the token so the link cannot be replayed.
        consent.setToken(null);
        studentConsentRepository.save(consent);

        student.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(student);
    }

    private static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() < 7) return "****";
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For can be comma-separated; the first entry is the original client.
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
