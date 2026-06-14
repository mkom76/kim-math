package com.example.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK wiring for FCM push delivery. The beans are created ONLY
 * when {@code app.push.enabled=true}; with push disabled (the default) no
 * Firebase beans exist and {@link com.example.service.PushNotificationService}
 * degrades to logging. So the app boots fine without any Firebase credentials.
 *
 * <p>When enabled, credentials are resolved from {@code app.push.fcm.credentials-path}
 * (a service-account JSON file). If that is blank, falls back to Application
 * Default Credentials (e.g. {@code GOOGLE_APPLICATION_CREDENTIALS}).
 */
@Configuration
@Slf4j
public class FcmConfig {

    @Bean
    @ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(@Value("${app.push.fcm.credentials-path:}") String credentialsPath)
            throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        if (StringUtils.hasText(credentialsPath)) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(in);
            }
            log.info("[fcm] initialized from service-account file: {}", credentialsPath);
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
            log.info("[fcm] initialized from application default credentials");
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
