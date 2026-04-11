-- Phase 1: 멀티테넌트 격리를 위한 스키마 추가
-- =====================================================================
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- MySQL 8 does not support `ADD COLUMN IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS`,
-- so the ALTER statements below are NOT idempotent. If this migration partially
-- succeeds, manually clean up before re-running:
--   DROP TABLE IF EXISTS teacher_academies;
--   ALTER TABLE academy_classes DROP FOREIGN KEY fk_class_owner;
--   ALTER TABLE academy_classes DROP COLUMN owner_teacher_id;
-- =====================================================================
-- 적용 시점: 코드 배포 전
-- 롤백: 위 cleanup 블록과 동일

CREATE TABLE IF NOT EXISTS teacher_academies (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id   BIGINT NOT NULL,
    academy_id   BIGINT NOT NULL,
    role         VARCHAR(20) NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_academy (teacher_id, academy_id),
    CONSTRAINT fk_ta_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id),
    CONSTRAINT fk_ta_academy FOREIGN KEY (academy_id) REFERENCES academies(id)
) ENGINE=InnoDB;

ALTER TABLE academy_classes
    ADD COLUMN owner_teacher_id BIGINT NULL;

ALTER TABLE academy_classes
    ADD CONSTRAINT fk_class_owner
    FOREIGN KEY (owner_teacher_id) REFERENCES teachers(id);
