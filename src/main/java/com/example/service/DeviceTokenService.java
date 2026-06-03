package com.example.service;

import com.example.entity.DeviceToken;
import com.example.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Upsert a (student, token) registration. If the token is already known
     * but belongs to a different student (e.g. a phone handed off between
     * siblings) ownership transfers to the calling student.
     */
    public void register(Long studentId, String token, String platform, String appVersion) {
        LocalDateTime now = LocalDateTime.now();
        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(token);
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setStudentId(studentId);
            dt.setPlatform(platform);
            dt.setAppVersion(appVersion);
            dt.setLastSeenAt(now);
            deviceTokenRepository.save(dt);
            return;
        }
        deviceTokenRepository.save(DeviceToken.builder()
                .studentId(studentId)
                .token(token)
                .platform(platform)
                .appVersion(appVersion)
                .lastSeenAt(now)
                .build());
    }

    /** Called on explicit logout so the device stops receiving pushes. */
    public void unregister(String token) {
        deviceTokenRepository.deleteByToken(token);
    }
}
