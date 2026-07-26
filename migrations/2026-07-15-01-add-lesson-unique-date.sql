-- A class can have at most one lesson per academy date.
-- Check for existing duplicate rows before applying this migration.
ALTER TABLE lessons
    ADD CONSTRAINT uq_lessons_academy_class_date
    UNIQUE (academy_id, class_id, lesson_date);
