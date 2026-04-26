-- Rollback: hide_scores_from_student 컬럼 제거
ALTER TABLE students
    DROP COLUMN hide_scores_from_student;
