-- 학생 일괄 등록 + 보호자 동의 플로우.
--
-- students 테이블 확장:
--   parent_name / parent_phone : 보호자 정보 (PIPA 동의자, 동의 링크 수신처)
--   contact_phone              : 학생 본인 연락처 (선택)
--   status                     : 'PENDING_CONSENT' | 'ACTIVE' | 'REVOKED'
--                                기존 행은 모두 ACTIVE (이미 오프라인 동의된 것으로 간주)
--
-- student_consents 테이블:
--   동의 토큰, 정책 버전, 서명 시각, 감사용 IP/UA. 학생당 1행 시작,
--   추후 정책 개정 시 재동의분이 새 행으로 누적될 수 있도록 별도 테이블로 분리.

ALTER TABLE students
    ADD COLUMN parent_name   VARCHAR(50)  NULL,
    ADD COLUMN parent_phone  VARCHAR(20)  NULL,
    ADD COLUMN contact_phone VARCHAR(20)  NULL,
    ADD COLUMN status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE student_consents (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    student_id          BIGINT       NOT NULL,
    consent_version     VARCHAR(20)  NOT NULL,
    -- 동의 전: 발급된 토큰. 동의 후에는 NULL로 무효화하여 링크 재사용 방지.
    token               VARCHAR(64)  NULL,
    token_issued_at     TIMESTAMP    NULL,
    token_expires_at    TIMESTAMP    NULL,
    -- 동의 완료 시각·감사 정보.
    consented_at        TIMESTAMP    NULL,
    signer_type         VARCHAR(20)  NULL,  -- 'PARENT' 등
    signer_phone_last4  VARCHAR(4)   NULL,  -- 본인 확인에 사용된 값
    signer_ip           VARCHAR(45)  NULL,
    signer_user_agent   VARCHAR(255) NULL,
    created_at          TIMESTAMP    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_student_consents_token (token),
    INDEX idx_student_consents_student (student_id),
    CONSTRAINT fk_student_consents_student
        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);
