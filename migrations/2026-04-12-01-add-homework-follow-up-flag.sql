-- 미완 숙제 재노출(follow-up) 플래그
-- 선생님이 학생-숙제 단위로 "다음에도 봐줘야 함" 마킹
--
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- MySQL 8 has no ADD COLUMN IF NOT EXISTS. Rollback via the *_rollback.sql sibling.
ALTER TABLE student_homeworks
  ADD COLUMN follow_up_flag BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_student_homeworks_follow_up
  ON student_homeworks (student_id, follow_up_flag);
