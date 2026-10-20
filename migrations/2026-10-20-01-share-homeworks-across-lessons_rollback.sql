-- Stop writes and return to the old backend only after this schema rollback succeeds.
-- For a complete return to pre-deployment data, restore the backup instead.
-- Refuse to discard new lesson links, progress sources, assignments or class history.
CREATE TEMPORARY TABLE homework_rollback_preflight (
    changed_lesson_links BIGINT NOT NULL DEFAULT 0,
    changed_progress_sources BIGINT NOT NULL DEFAULT 0,
    changed_assignments BIGINT NOT NULL DEFAULT 0,
    changed_class_history BIGINT NOT NULL DEFAULT 0,
    duplicate_clinic_snapshots BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_homework_rollback_lesson_links CHECK (changed_lesson_links = 0),
    CONSTRAINT ck_homework_rollback_progress_sources CHECK (changed_progress_sources = 0),
    CONSTRAINT ck_homework_rollback_assignments CHECK (changed_assignments = 0),
    CONSTRAINT ck_homework_rollback_class_history CHECK (changed_class_history = 0),
    CONSTRAINT ck_homework_rollback_clinic CHECK (duplicate_clinic_snapshots = 0)
);
INSERT INTO homework_rollback_preflight (changed_lesson_links)
SELECT COUNT(*) FROM lesson_homeworks lh JOIN homeworks h ON h.id = lh.homework_id
WHERE NOT (lh.lesson_id <=> h.lesson_id);

INSERT INTO homework_rollback_preflight (changed_progress_sources)
SELECT COUNT(*) FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
WHERE NOT (sh.lesson_id <=> h.lesson_id);

INSERT INTO homework_rollback_preflight (changed_assignments)
SELECT COUNT(*) FROM student_lessons sl
WHERE sl.homework_assignment_set = TRUE AND (sl.assigned_homework_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
    WHERE sh.student_id = sl.student_id AND sh.homework_id = sl.assigned_homework_id AND h.lesson_id = sl.lesson_id
));

INSERT INTO homework_rollback_preflight (changed_class_history)
SELECT COUNT(*) FROM student_homework_classes shc
JOIN student_homeworks sh ON sh.id = shc.student_homework_id
JOIN homeworks h ON h.id = sh.homework_id LEFT JOIN lessons l ON l.id = h.lesson_id
WHERE shc.class_id <> COALESCE(l.class_id, h.class_id);

INSERT INTO homework_rollback_preflight (duplicate_clinic_snapshots)
SELECT COUNT(*) FROM (
    SELECT clinic_id, student_id, homework_id FROM clinic_homework_progress
    GROUP BY clinic_id, student_id, homework_id HAVING COUNT(*) > 1
) duplicate_snapshots;
DROP TEMPORARY TABLE homework_rollback_preflight;

-- Restore the clinic uniqueness used by 002_add_clinic_homework_progress.sql.
ALTER TABLE clinic_homework_progress
    ADD CONSTRAINT uk_clinic_student_homework UNIQUE (clinic_id, student_id, homework_id),
    DROP FOREIGN KEY fk_clinic_homework_progress_record,
    DROP INDEX uk_clinic_homework_progress_record,
    DROP COLUMN student_homework_id;
DROP TABLE student_homework_classes;
-- MySQL may replace an implicit student_id FK index with the new composite unique key.
ALTER TABLE student_homeworks
    ADD INDEX idx_student_homeworks_student_rollback (student_id),
    DROP INDEX uk_student_homeworks_student_homework_lesson,
    DROP FOREIGN KEY fk_student_homeworks_lesson,
    DROP COLUMN lesson_scope_id,
    DROP COLUMN lesson_id;
ALTER TABLE student_lessons
    DROP FOREIGN KEY fk_student_lessons_assigned_homework,
    DROP INDEX idx_student_lessons_assigned_homework,
    DROP COLUMN assigned_homework_id,
    DROP COLUMN homework_assignment_set;
DROP TABLE lesson_homeworks;
