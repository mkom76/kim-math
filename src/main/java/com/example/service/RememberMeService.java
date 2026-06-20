package com.example.service;

import com.example.entity.RememberMeToken;
import com.example.repository.RememberMeTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RememberMeService {

    public static final String COOKIE_NAME = "KM_REMEMBER";

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RememberMeTokenRepository rememberMeTokenRepository;

    @Value("${app.security.remember-me.ttl-days:30}")
    private long ttlDays;

    @Value("${app.security.remember-me.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.remember-me.same-site:Strict}")
    private String sameSite;

    @Transactional
    public void issue(HttpServletResponse response, String userRole, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String selector = randomToken();
        String validator = randomToken();

        RememberMeToken token = RememberMeToken.builder()
                .selectorHash(hash(selector))
                .validatorHash(hash(validator))
                .userRole(userRole)
                .userId(userId)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(ttlDays))
                .build();
        rememberMeTokenRepository.save(token);
        addCookie(response, selector + "." + validator, Duration.ofDays(ttlDays));
    }

    @Transactional
    public Optional<RememberedLogin> consume(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = cookieValue(request);
        if (!StringUtils.hasText(cookieValue)) {
            return Optional.empty();
        }

        String[] parts = cookieValue.split("\\.", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            clearCookie(response);
            return Optional.empty();
        }

        String selector = parts[0];
        String validator = parts[1];
        Optional<RememberMeToken> found = rememberMeTokenRepository.findBySelectorHash(hash(selector));
        if (found.isEmpty()) {
            clearCookie(response);
            return Optional.empty();
        }

        RememberMeToken token = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            revoke(token, now);
            clearCookie(response);
            return Optional.empty();
        }

        if (!matchesHash(validator, token.getValidatorHash())) {
            revoke(token, now);
            clearCookie(response);
            return Optional.empty();
        }

        String nextValidator = randomToken();
        token.setValidatorHash(hash(nextValidator));
        token.setLastUsedAt(now);
        rememberMeTokenRepository.save(token);
        addCookie(response, selector + "." + nextValidator, Duration.between(now, token.getExpiresAt()));

        return Optional.of(new RememberedLogin(token.getUserRole(), token.getUserId()));
    }

    @Transactional
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = cookieValue(request);
        if (StringUtils.hasText(cookieValue)) {
            String[] parts = cookieValue.split("\\.", 2);
            if (parts.length == 2) {
                rememberMeTokenRepository.findBySelectorHash(hash(parts[0]))
                        .ifPresent(token -> revoke(token, LocalDateTime.now()));
            }
        }
        clearCookie(response);
    }

    private void revoke(RememberMeToken token, LocalDateTime now) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(now);
            rememberMeTokenRepository.save(token);
        }
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean matchesHash(String raw, String expectedHash) {
        return MessageDigest.isEqual(
                hash(raw).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    public record RememberedLogin(String userRole, Long userId) {}
}
