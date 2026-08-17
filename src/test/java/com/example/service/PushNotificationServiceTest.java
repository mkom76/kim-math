package com.example.service;

import com.example.entity.DeviceToken;
import com.example.repository.DeviceTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotificationServiceTest {

    private DeviceTokenRepository deviceTokenRepository;
    private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private StudentNotificationService studentNotificationService;
    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        deviceTokenRepository = mock(DeviceTokenRepository.class);
        firebaseMessagingProvider = mock(ObjectProvider.class);
        studentNotificationService = mock(StudentNotificationService.class);
        service = new PushNotificationService(
                deviceTokenRepository, firebaseMessagingProvider, studentNotificationService);
    }

    @Test
    void empty_student_list_reports_no_targets() {
        PushNotificationService.DeliveryResult result = service.sendToStudents(
                List.of(), "title", "body", Map.of());

        assertThat(result).isEqualTo(PushNotificationService.DeliveryResult.empty());
        verify(deviceTokenRepository, never()).findByStudentIdIn(any());
    }

    @Test
    void students_without_registered_devices_are_not_reported_as_sent() {
        when(deviceTokenRepository.findByStudentIdIn(List.of(10L, 20L))).thenReturn(List.of());

        PushNotificationService.DeliveryResult result = service.sendToStudents(
                List.of(10L, 10L, 20L), "title", "body", Map.of());

        assertThat(result.targetStudentCount()).isEqualTo(2);
        assertThat(result.registeredDeviceCount()).isZero();
        assertThat(result.sentStudentCount()).isZero();
        verify(studentNotificationService).createForStudents(
                List.of(10L, 20L), null, "title", "body", null, null);
    }

    @Test
    void disabled_push_does_not_report_registered_devices_as_sent() {
        DeviceToken token = token(1L, 10L, "token-1");
        when(deviceTokenRepository.findByStudentIdIn(List.of(10L))).thenReturn(List.of(token));
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(service, "pushEnabled", false);

        PushNotificationService.DeliveryResult result = service.sendToStudents(
                List.of(10L), "title", "body", Map.of());

        assertThat(result.targetStudentCount()).isEqualTo(1);
        assertThat(result.registeredDeviceCount()).isEqualTo(1);
        assertThat(result.sentStudentCount()).isZero();
    }

    @Test
    void successful_devices_are_counted_as_distinct_students() throws Exception {
        DeviceToken firstDevice = token(1L, 10L, "token-1");
        DeviceToken secondDevice = token(2L, 10L, "token-2");
        DeviceToken thirdDevice = token(3L, 20L, "token-3");
        List<DeviceToken> tokens = List.of(firstDevice, secondDevice, thirdDevice);
        when(deviceTokenRepository.findByStudentIdIn(List.of(10L, 20L))).thenReturn(tokens);

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        ReflectionTestUtils.setField(service, "pushEnabled", true);

        SendResponse firstSuccess = mock(SendResponse.class);
        SendResponse secondSuccess = mock(SendResponse.class);
        SendResponse thirdSuccess = mock(SendResponse.class);
        when(firstSuccess.isSuccessful()).thenReturn(true);
        when(secondSuccess.isSuccessful()).thenReturn(true);
        when(thirdSuccess.isSuccessful()).thenReturn(true);

        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(List.of(firstSuccess, secondSuccess, thirdSuccess));
        when(response.getSuccessCount()).thenReturn(3);
        when(messaging.sendEachForMulticast(any())).thenReturn(response);

        PushNotificationService.DeliveryResult result = service.sendToStudents(
                List.of(10L, 20L), "title", "body", Map.of("path", "/student/daily-feedback"));

        assertThat(result.targetStudentCount()).isEqualTo(2);
        assertThat(result.registeredDeviceCount()).isEqualTo(3);
        assertThat(result.sentStudentCount()).isEqualTo(2);
    }

    @Test
    void source_key_is_forwarded_to_the_native_push_payload() throws Exception {
        DeviceToken token = token(1L, 10L, "token-1");
        when(deviceTokenRepository.findByStudentIdIn(List.of(10L))).thenReturn(List.of(token));

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(messaging);
        ReflectionTestUtils.setField(service, "pushEnabled", true);

        SendResponse success = mock(SendResponse.class);
        when(success.isSuccessful()).thenReturn(true);
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(List.of(success));
        when(response.getSuccessCount()).thenReturn(1);
        when(messaging.sendEachForMulticast(any())).thenAnswer(invocation -> {
            MulticastMessage message = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Map<String, String> payload = (Map<String, String>) ReflectionTestUtils.getField(message, "data");
            assertThat(payload).containsEntry("sourceKey", "lesson-feedback:55");
            return response;
        });

        service.sendToStudents(
                List.of(10L), "title", "body",
                Map.of("path", "/student/daily-feedback", "type", "FEEDBACK"),
                "lesson-feedback:55");
    }

    private DeviceToken token(Long id, Long studentId, String token) {
        return DeviceToken.builder()
                .id(id)
                .studentId(studentId)
                .token(token)
                .platform("android")
                .build();
    }
}
