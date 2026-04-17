-- Migration: Rename completion column to incorrect_count in student_homeworks table
-- Date: 2025-12-28
-- Description: Changes the data model from storing completion percentage to storing incorrect count

-- Step 1: Add the new incorrect_count column
ALTER TABLE student_homeworks
ADD COLUMN incorrect_count INT DEFAULT 0 COMMENT '오답 개수';

-- Step 2: Migrate data from completion to incorrect_count
-- Formula: incorrect_count = total_questions - (total_questions * completion / 100)
-- Note: This requires a join with the homeworks table to get question_count
UPDATE student_homeworks sh
JOIN homeworks h ON sh.homework_id = h.id
SET sh.incorrect_count = ROUND(h.question_count * (1 - COALESCE(sh.completion, 0) / 100.0))
WHERE sh.completion IS NOT NULL;

-- Step 3: Drop the old completion column
ALTER TABLE student_homeworks
DROP COLUMN completion;

-- Verification query (run this after migration to verify):
-- SELECT
--     sh.id,
--     sh.incorrect_count,
--     h.question_count,
--     ROUND((h.question_count - sh.incorrect_count) / h.question_count * 100) as calculated_completion
-- FROM student_homeworks sh
-- JOIN homeworks h ON sh.homework_id = h.id;
