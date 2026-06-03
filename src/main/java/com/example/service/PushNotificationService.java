package com.example.service;

import com.example.entity.DeviceToken;
import com.example.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Send push notifications to a set of students. This is the contract used by
 * trigger code (e.g. "new homework", "test starts soon"). The actual FCM HTTP
 * v1 transport lives behind {@link #send} — for now it just logs because the
 * Firebase project + service-account credentials are not yet provisioned.
 *
 * <p>To wire real sending (Phase 4b):
 * <ol>
 *   <li>Create a Firebase project, add an Android app, download
 *       {@code google-services.json} into {@code frontend/android/app/}.</li>
 *   <li>Generate a service-account JSON for FCM HTTP v1 and put its path /
 *       contents in env vars ({@code FCM_PROJECT_ID}, {@code FCM_CREDENTIALS_JSON}).</li>
 *   <li>Add the Firebase Admin SDK dependency to {@code build.gradle}.</li>
 *   <li>Replace the log block in {@link #send} with the Admin SDK call.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Value("${app.push.enabled:false}")
    private boolean pushEnabled;

    /**
     * Build a notification + send to every device registered for the given
     * students. Data map is forwarded as the FCM `data` payload — the
     * frontend handler uses it to deep-link (e.g. {@code path: "/student/tests/123"}).
     */
    public void sendToStudents(List<Long> studentIds,
                               String title,
                               String body,
                               Map<String, String> data) {
        if (studentIds == null || studentIds.isEmpty()) return;
        List<DeviceToken> tokens = deviceTokenRepository.findByStudentIdIn(studentIds);
        if (tokens.isEmpty()) return;
        send(tokens, title, body, data);
    }

    private void send(List<DeviceToken> tokens, String title, String body, Map<String, String> data) {
        if (!pushEnabled) {
            log.info("[push:disabled] {} → {}/{} (data={})",
                    tokens.stream().map(DeviceToken::getId).toList(), title, body, data);
            return;
        }
        // TODO Phase 4b: call FCM HTTP v1 via Firebase Admin SDK here.
        log.warn("[push:enabled-but-not-wired] {} recipients — install Firebase Admin SDK to deliver",
                tokens.size());
    }
}
