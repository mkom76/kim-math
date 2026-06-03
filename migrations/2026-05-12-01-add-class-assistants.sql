-- 반-조교(ASSISTANT) 매핑 테이블. 한 반에 조교 여러 명, 한 조교가 여러 반 보조 가능.
-- TEACHER 의 owner_teacher_id 와는 별개의 권한 채널. 조교는 teacher_academies.role = 'ASSISTANT'.
--
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- MySQL 8 has no CREATE TABLE IF NOT EXISTS guards under this project's rule.
-- Rollback via the *_rollback.sql sibling.

CREATE TABLE class_assistants (
    class_id   BIGINT    NOT NULL,
    teacher_id BIGINT    NOT NULL,
    created_at TIMESTAMP NULL,
    PRIMARY KEY (class_id, teacher_id),
    INDEX idx_class_assistants_teacher (teacher_id),
    CONSTRAINT fk_class_assistants_class
        FOREIGN KEY (class_id)   REFERENCES academy_classes(id) ON DELETE CASCADE,
    CONSTRAINT fk_class_assistants_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers(id)        ON DELETE CASCADE
);
