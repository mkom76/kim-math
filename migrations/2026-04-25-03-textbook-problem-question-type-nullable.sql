-- TextbookProblem.question_type을 nullable로 변경
-- 시험 출제 시 스냅샷에서 null이면 디폴트(SUBJECTIVE) 적용. 빈 값으로 교재 등록 가능.
--
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- Rollback via the *_rollback.sql sibling.
ALTER TABLE textbook_problems
    MODIFY COLUMN question_type VARCHAR(20) NULL;
