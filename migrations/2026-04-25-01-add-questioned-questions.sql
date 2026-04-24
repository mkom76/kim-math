-- 학생이 질문하고 싶은 문항번호를 저장하는 컬럼 추가
-- 형식: 콤마 구분된 정렬·중복제거된 문항번호 (예: "3,7,12")
--
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- MySQL 8 has no ADD COLUMN IF NOT EXISTS. Rollback via the *_rollback.sql sibling.
ALTER TABLE student_homeworks
  ADD COLUMN questioned_questions VARCHAR(500) NULL;
