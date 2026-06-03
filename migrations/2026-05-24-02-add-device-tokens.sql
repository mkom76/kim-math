-- 푸시 알림 발송 대상 기기 토큰.
--
-- 한 학생이 여러 기기에 동시 로그인할 수 있으므로 (student_id, token) 한 쌍이
-- 하나의 row. 토큰 자체는 FCM/APNs 발급 토큰으로, 갱신·만료 시 클라이언트가
-- 다시 등록하면 last_seen_at만 업데이트되도록 ON DUPLICATE KEY 처리.

CREATE TABLE device_tokens (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    student_id    BIGINT       NOT NULL,
    token         VARCHAR(255) NOT NULL,
    platform      VARCHAR(16)  NOT NULL,  -- 'android' | 'ios'
    app_version   VARCHAR(32)  NULL,
    created_at    TIMESTAMP    NULL,
    last_seen_at  TIMESTAMP    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_device_tokens_token (token),
    INDEX idx_device_tokens_student (student_id),
    CONSTRAINT fk_device_tokens_student
        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);
