-- Rollback Migration: Restore completion column from incorrect_count
-- Date: 2025-12-28
-- Description: Reverts the changes from V1 migration

-- Step 1: Add back the completion column
ALTER TABLE student_homeworks
ADD COLUMN completion INT COMMENT '완성도 (0-100)';

-- Step 2: Migrate data from incorrect_count back to completion
-- Formula: completion = (total_questions - incorrect_count) / total_questions * 100
UPDATE student_homeworks sh
JOIN homeworks h ON sh.homework_id = h.id
SET sh.completion = ROUND((h.question_count - COALESCE(sh.incorrect_count, 0)) / h.question_count * 100)
WHERE sh.incorrect_count IS NOT NULL;

-- Step 3: Drop the incorrect_count column
ALTER TABLE student_homeworks
DROP COLUMN incorrect_count;
