package com.example.service;

import com.example.entity.DeviceToken;
import com.example.repository.DeviceTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Send push notifications to a set of students. This is the contract used by
 * trigger code (e.g. "daily feedback published"). The actual FCM HTTP v1
 * transport runs through the Firebase Admin SDK when push is enabled; otherwise
 * it just logs (so the app works without any Firebase provisioning).
 *
 * <p>{@link FirebaseMessaging} is injected via {@link ObjectProvider} because the
 * bean exists only when {@code app.push.enabled=true} (see {@code FcmConfig}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Value("${app.push.enabled:false}")
    private boolean pushEnabled;

    /**
     * Build a notification + send to every device registered for the given
     * students. Data map is forwarded as the FCM `data` payload — the
     * frontend handler uses it to deep-link (e.g. {@code path: "/student/daily-feedback"}).
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
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (!pushEnabled || messaging == null) {
            log.info("[push:disabled] {} → {}/{} (data={})",
                    tokens.stream().map(DeviceToken::getId).toList(), title, body, data);
            return;
        }

        List<String> tokenStrings = tokens.stream().map(DeviceToken::getToken).toList();
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .addAllTokens(tokenStrings)
                .build();

        try {
            BatchResponse response = messaging.sendEachForMulticast(message);
            reapInvalidTokens(response, tokenStrings);
            log.info("[push] sent {}/{} success (title={})",
                    response.getSuccessCount(), tokenStrings.size(), title);
        } catch (FirebaseMessagingException e) {
            log.error("[push] multicast send failed (title={})", title, e);
        }
    }

    /** Delete tokens FCM reports as gone (uninstalled / rotated) so they don't linger. */
    private void reapInvalidTokens(BatchResponse response, List<String> tokenStrings) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful() || r.getException() == null) continue;
            MessagingErrorCode code = r.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                deviceTokenRepository.deleteByToken(tokenStrings.get(i));
                log.info("[push] reaped invalid token (code={})", code);
            }
        }
    }
}
