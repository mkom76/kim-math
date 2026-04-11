-- Phase 4: NOT NULL 제약 강화
-- =====================================================================
-- ⚠ ONE-SHOT MIGRATION
-- 적용 시점: Phase 3 코드 배포 후 안정화 검증이 끝난 뒤
--
-- 사전 체크 (반드시 0이어야 함):
--   SELECT COUNT(*) FROM academy_classes WHERE owner_teacher_id IS NULL;
--
-- 0이 아니면 이 마이그레이션을 보류하고 누락된 반을 먼저 정리할 것.
--
-- 롤백:
--   ALTER TABLE academy_classes MODIFY COLUMN owner_teacher_id BIGINT NULL;
-- =====================================================================

ALTER TABLE academy_classes
    MODIFY COLUMN owner_teacher_id BIGINT NOT NULL;
