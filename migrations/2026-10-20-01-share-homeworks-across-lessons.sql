-- MySQL 8.0.16+. One-shot migration from the schema BEFORE any homework-sharing draft.
-- Back up the DB and stop application writes. Run without mysql --force, then deploy the backend.
-- Shared homework content; independent student progress for each lesson.

-- 1. Reject partial/repeated migrations and ambiguous legacy data before persistent DDL.
CREATE TEMPORARY TABLE homework_migration_preflight (
    existing_schema BIGINT NOT NULL DEFAULT 0,
    duplicate_progress BIGINT NOT NULL DEFAULT 0,
    duplicate_assignments BIGINT NOT NULL DEFAULT 0,
    duplicate_clinic_snapshots BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_homework_migration_existing_schema CHECK (existing_schema = 0),
    CONSTRAINT ck_homework_migration_duplicate_progress CHECK (duplicate_progress = 0),
    CONSTRAINT ck_homework_migration_duplicate_assignments CHECK (duplicate_assignments = 0),
    CONSTRAINT ck_homework_migration_duplicate_clinic CHECK (duplicate_clinic_snapshots = 0)
);

INSERT INTO homework_migration_preflight (existing_schema)
SELECT
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND (
        (table_name = 'student_lessons' AND column_name IN ('assigned_homework_id', 'homework_assignment_set'))
        OR (table_name = 'student_homeworks' AND column_name IN ('lesson_id', 'lesson_scope_id'))
        OR (table_name = 'clinic_homework_progress' AND column_name = 'student_homework_id')))
    + (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
        AND table_name IN ('lesson_homeworks', 'student_homework_classes'))
    + (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'student_homeworks' AND index_name = 'uk_student_homeworks_student_homework');

INSERT INTO homework_migration_preflight (duplicate_progress)
SELECT COUNT(*) FROM (
    SELECT student_id, homework_id FROM student_homeworks
    GROUP BY student_id, homework_id HAVING COUNT(*) > 1
) duplicate_records;

INSERT INTO homework_migration_preflight (duplicate_assignments)
SELECT COUNT(*) FROM (
    SELECT sh.student_id, h.lesson_id FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
    WHERE h.lesson_id IS NOT NULL
    GROUP BY sh.student_id, h.lesson_id HAVING COUNT(*) > 1
) duplicate_lesson_assignments;

INSERT INTO homework_migration_preflight (duplicate_clinic_snapshots)
SELECT COUNT(*) FROM (
    SELECT clinic_id, student_id, homework_id FROM clinic_homework_progress
    GROUP BY clinic_id, student_id, homework_id HAVING COUNT(*) > 1
) duplicate_snapshots;
DROP TEMPORARY TABLE homework_migration_preflight;

-- 2. Create the final schema directly. Do not introduce a student/homework-only unique constraint.
CREATE TABLE lesson_homeworks (
    lesson_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    PRIMARY KEY (lesson_id, homework_id),
    INDEX idx_lesson_homeworks_homework (homework_id),
    CONSTRAINT fk_lesson_homeworks_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT fk_lesson_homeworks_homework FOREIGN KEY (homework_id) REFERENCES homeworks (id)
);

ALTER TABLE student_lessons
    ADD COLUMN assigned_homework_id BIGINT NULL,
    ADD COLUMN homework_assignment_set BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_student_lessons_assigned_homework (assigned_homework_id),
    ADD CONSTRAINT fk_student_lessons_assigned_homework FOREIGN KEY (assigned_homework_id) REFERENCES homeworks (id);

ALTER TABLE student_homeworks
    ADD COLUMN lesson_id BIGINT NULL,
    ADD COLUMN lesson_scope_id BIGINT GENERATED ALWAYS AS (COALESCE(lesson_id, 0)) STORED,
    ADD CONSTRAINT fk_student_homeworks_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    ADD CONSTRAINT uk_student_homeworks_student_homework_lesson UNIQUE (student_id, homework_id, lesson_scope_id);

-- Keep the original class even when the lesson's current homework assignment is removed.
CREATE TABLE student_homework_classes (
    student_homework_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    PRIMARY KEY (student_homework_id, class_id),
    INDEX idx_student_homework_classes_class (class_id, student_homework_id),
    CONSTRAINT fk_student_homework_classes_record FOREIGN KEY (student_homework_id)
        REFERENCES student_homeworks (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_homework_classes_class FOREIGN KEY (class_id) REFERENCES academy_classes (id)
);

ALTER TABLE clinic_homework_progress
    ADD COLUMN student_homework_id BIGINT NULL,
    ADD CONSTRAINT fk_clinic_homework_progress_record FOREIGN KEY (student_homework_id)
        REFERENCES student_homeworks (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_clinic_homework_progress_record UNIQUE (clinic_id, student_homework_id);

-- 002_add_clinic_homework_progress.sql defined this old unique key. Hibernate-created DBs may lack it.
-- Leaving it in place would reject two lesson records for the same homework in one clinic.
SET @homework_drop_clinic_unique = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'clinic_homework_progress' AND index_name = 'uk_clinic_student_homework'),
    'ALTER TABLE clinic_homework_progress DROP INDEX uk_clinic_student_homework',
    'DO 0'
);
PREPARE homework_clinic_index_statement FROM @homework_drop_clinic_unique;
EXECUTE homework_clinic_index_statement;
DEALLOCATE PREPARE homework_clinic_index_statement;
SET @homework_drop_clinic_unique = NULL;

-- 3. Backfill in one transaction. DDL above auto-commits in MySQL; this is not an atomic schema change.
START TRANSACTION;
INSERT INTO lesson_homeworks (lesson_id, homework_id)
SELECT lesson_id, id FROM homeworks WHERE lesson_id IS NOT NULL;

-- Retain legacy homeworks.lesson_id. NULL stays NULL for records entered without a lesson.
UPDATE student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
SET sh.lesson_id = h.lesson_id, sh.updated_at = sh.updated_at
WHERE sh.lesson_id IS NULL AND h.lesson_id IS NOT NULL;

INSERT INTO student_lessons (student_id, lesson_id, assigned_homework_id, homework_assignment_set,
                             is_ai_feedback, created_at, updated_at)
SELECT sh.student_id, sh.lesson_id, sh.homework_id, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM student_homeworks sh WHERE sh.lesson_id IS NOT NULL
ON DUPLICATE KEY UPDATE assigned_homework_id = VALUES(assigned_homework_id),
                        homework_assignment_set = TRUE, updated_at = student_lessons.updated_at;

INSERT INTO student_homework_classes (student_homework_id, class_id)
SELECT sh.id, COALESCE(l.class_id, h.class_id)
FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
LEFT JOIN lessons l ON l.id = sh.lesson_id;

-- A snapshot without a matching record in the clinic's class keeps its original values and no record link.
UPDATE clinic_homework_progress cp JOIN student_homeworks sh
    ON sh.student_id = cp.student_id AND sh.homework_id = cp.homework_id
JOIN clinics c ON c.id = cp.clinic_id
JOIN student_homework_classes shc ON shc.student_homework_id = sh.id AND shc.class_id = c.class_id
SET cp.student_homework_id = sh.id, cp.updated_at = cp.updated_at
WHERE cp.student_homework_id IS NULL;
COMMIT;

-- 4. Verification: every issue_count must be 0 before starting the application.
SELECT 'legacy_lesson_links' AS check_name, COUNT(*) AS issue_count
FROM homeworks h LEFT JOIN lesson_homeworks lh ON lh.homework_id = h.id AND lh.lesson_id = h.lesson_id
WHERE h.lesson_id IS NOT NULL AND lh.homework_id IS NULL
UNION ALL
SELECT 'progress_lesson', COUNT(*)
FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
WHERE NOT (sh.lesson_id <=> h.lesson_id)
UNION ALL
SELECT 'progress_class', COUNT(*)
FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
LEFT JOIN lessons l ON l.id = sh.lesson_id
LEFT JOIN student_homework_classes shc ON shc.student_homework_id = sh.id
WHERE shc.class_id IS NULL OR shc.class_id <> COALESCE(l.class_id, h.class_id)
UNION ALL
SELECT 'student_assignment', COUNT(*)
FROM student_homeworks sh LEFT JOIN student_lessons sl ON sl.student_id = sh.student_id AND sl.lesson_id = sh.lesson_id
WHERE sh.lesson_id IS NOT NULL
    AND (sl.id IS NULL OR NOT (sl.assigned_homework_id <=> sh.homework_id) OR sl.homework_assignment_set = FALSE);
