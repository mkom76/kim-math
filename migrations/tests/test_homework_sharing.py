"""Exercise the consolidated migration in disposable databases inside a test-only MySQL container."""
import argparse
import subprocess
import unittest
import uuid
from pathlib import Path

MIGRATIONS = Path(__file__).resolve().parents[1]
FORWARD = (MIGRATIONS / '2026-10-20-01-share-homeworks-across-lessons.sql').read_text()
ROLLBACK = (MIGRATIONS / '2026-10-20-01-share-homeworks-across-lessons_rollback.sql').read_text()
CLINIC_SCHEMA = (MIGRATIONS / '002_add_clinic_homework_progress.sql').read_text()

# Include ON UPDATE timestamps to prove that backfill does not rewrite historical times.
BASE_SCHEMA = '''
CREATE TABLE academy_classes (id BIGINT PRIMARY KEY);
CREATE TABLE students (id BIGINT PRIMARY KEY);
CREATE TABLE lessons (id BIGINT PRIMARY KEY, class_id BIGINT NOT NULL,
    FOREIGN KEY (class_id) REFERENCES academy_classes(id));
CREATE TABLE homeworks (id BIGINT PRIMARY KEY, class_id BIGINT NOT NULL, lesson_id BIGINT NULL,
    FOREIGN KEY (class_id) REFERENCES academy_classes(id), FOREIGN KEY (lesson_id) REFERENCES lessons(id));
CREATE TABLE student_homeworks (
    id BIGINT PRIMARY KEY, student_id BIGINT NOT NULL, homework_id BIGINT NOT NULL,
    incorrect_count INT, unsolved_count INT, incorrect_questions VARCHAR(500), unsolved_questions VARCHAR(500),
    questioned_questions VARCHAR(500), follow_up_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6), updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (student_id) REFERENCES students(id), FOREIGN KEY (homework_id) REFERENCES homeworks(id));
CREATE TABLE student_lessons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL,
    instructor_feedback TEXT, feedback_author VARCHAR(255), is_ai_feedback BOOLEAN, attendance_status VARCHAR(20),
    created_at DATETIME(6), updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE (student_id, lesson_id), FOREIGN KEY (student_id) REFERENCES students(id),
    FOREIGN KEY (lesson_id) REFERENCES lessons(id));
CREATE TABLE clinics (id BIGINT PRIMARY KEY, class_id BIGINT NOT NULL);
INSERT INTO academy_classes VALUES (10), (20);
INSERT INTO students VALUES (1), (2);
INSERT INTO lessons VALUES (1,10), (2,10), (3,20);
INSERT INTO homeworks VALUES (100,10,1), (200,10,NULL), (300,20,3);
INSERT INTO student_homeworks VALUES
    (1,1,100,2,1,'1,2','3','5',TRUE,'2026-09-01','2026-09-02'),
    (2,2,200,NULL,NULL,NULL,NULL,NULL,FALSE,'2026-09-03','2026-09-04'),
    (3,2,300,1,0,'1','','',FALSE,'2026-09-05','2026-09-06');
INSERT INTO student_lessons (student_id,lesson_id,instructor_feedback,feedback_author,is_ai_feedback,
    attendance_status,created_at,updated_at) VALUES
    (1,1,'preserved feedback','teacher',TRUE,'PRESENT','2026-09-01','2026-09-02'),
    (2,2,'unrelated lesson','another teacher',FALSE,'ABSENT','2026-09-03','2026-09-04');
INSERT INTO clinics VALUES (10,10), (11,20);
'''
FIXTURES = '''
ALTER TABLE clinic_homework_progress MODIFY updated_at DATETIME(6)
    DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
INSERT INTO clinic_homework_progress (id,clinic_id,student_id,homework_id,incorrect_count_before,
    unsolved_count_before,incorrect_count_after,unsolved_count_after,created_at,updated_at) VALUES
    (1,10,1,100,3,1,2,1,'2026-09-01','2026-09-02'),
    (9,11,1,100,3,1,2,1,'2026-09-03','2026-09-04'),
    (10,10,1,200,4,0,3,0,'2026-09-05','2026-09-06');
'''
PROGRESS_COLUMNS = ('id,student_id,homework_id,incorrect_count,unsolved_count,incorrect_questions,'
                    'unsolved_questions,questioned_questions,follow_up_flag,created_at,updated_at')
LESSON_COLUMNS = ('id,student_id,lesson_id,instructor_feedback,feedback_author,is_ai_feedback,'
                  'attendance_status,created_at,updated_at')
CLINIC_COLUMNS = ('id,clinic_id,student_id,homework_id,incorrect_count_before,unsolved_count_before,'
                  'incorrect_count_after,unsolved_count_after,created_at,updated_at')
TABLES = ('academy_classes', 'students', 'lessons', 'homeworks', 'student_homeworks',
          'student_lessons', 'clinics', 'clinic_homework_progress')


class HomeworkSharingMigrationTest(unittest.TestCase):
    container = None

    def sql(self, query, db=None, check=True):
        args = ['docker', 'exec', '-i', self.container, 'mysql', '-uroot', '--batch', '--skip-column-names']
        if db:
            args.append(db)
        result = subprocess.run(args, input=query, text=True, capture_output=True, timeout=60)
        if check and result.returncode:
            self.fail(result.stderr)
        return result

    def database(self, legacy_clinic_index=True):
        db = 'hw_ddl_test_' + uuid.uuid4().hex
        self.sql(f'CREATE DATABASE {db}')
        self.addCleanup(self.sql, f'DROP DATABASE IF EXISTS {db}')
        self.sql(BASE_SCHEMA + CLINIC_SCHEMA + FIXTURES, db)
        if not legacy_clinic_index:
            self.sql('ALTER TABLE clinic_homework_progress DROP INDEX uk_clinic_student_homework', db)
        return db

    def value(self, query, db):
        return self.sql(query, db).stdout.strip()

    def schema(self, db):
        return '\n'.join(self.value('SHOW CREATE TABLE ' + table, db) for table in TABLES)

    def assert_rejected_before_ddl(self, query, db, constraint):
        before = self.schema(db)
        failed = self.sql(query, db, check=False)
        self.assertNotEqual(failed.returncode, 0, 'Migration unexpectedly succeeded')
        self.assertIn(constraint, failed.stderr)
        self.assertEqual(self.schema(db), before, 'Persistent schema changed before rejection')
        return failed

    def test_forward_preserves_data_and_supports_two_lessons_in_one_clinic(self):
        db = self.database()
        progress_before = self.value('SELECT ' + PROGRESS_COLUMNS + ' FROM student_homeworks ORDER BY id', db)
        feedback_before = self.value('SELECT ' + LESSON_COLUMNS + ' FROM student_lessons ORDER BY id', db)
        clinic_before = self.value('SELECT ' + CLINIC_COLUMNS + ' FROM clinic_homework_progress ORDER BY id', db)
        result = self.sql(FORWARD, db)
        self.assertEqual(result.stdout.strip().splitlines(), [
            'legacy_lesson_links\t0', 'progress_lesson\t0', 'progress_class\t0', 'student_assignment\t0'])
        self.assertEqual(self.value('SELECT ' + PROGRESS_COLUMNS + ' FROM student_homeworks ORDER BY id', db), progress_before)
        self.assertEqual(self.value('SELECT ' + LESSON_COLUMNS + ' FROM student_lessons WHERE id IN (1,2) ORDER BY id', db), feedback_before)
        self.assertEqual(self.value('SELECT ' + CLINIC_COLUMNS + ' FROM clinic_homework_progress ORDER BY id', db), clinic_before)
        self.assertEqual(self.value('SELECT id,COALESCE(lesson_id,0),lesson_scope_id FROM student_homeworks ORDER BY id', db),
                         '1\t1\t1\n2\t0\t0\n3\t3\t3')
        self.assertEqual(self.value('SELECT * FROM student_homework_classes ORDER BY student_homework_id', db),
                         '1\t10\n2\t10\n3\t20')
        self.assertEqual(self.value('SELECT id,COALESCE(student_homework_id,0) FROM clinic_homework_progress ORDER BY id', db),
                         '1\t1\n9\t0\n10\t0')
        self.assertEqual(self.value('SELECT assigned_homework_id,homework_assignment_set FROM student_lessons WHERE student_id=2 AND lesson_id=3', db), '300\t1')
        self.assert_new_lesson_record_allowed(db)
        self.assertNotEqual(self.sql('INSERT INTO student_homeworks(id,student_id,homework_id,lesson_id) VALUES(5,1,100,2)', db, False).returncode, 0)
        self.assertNotEqual(self.sql('INSERT INTO student_homeworks(id,student_id,homework_id) VALUES(5,2,200)', db, False).returncode, 0)
        self.assertNotEqual(self.sql('INSERT INTO clinic_homework_progress(id,clinic_id,student_id,homework_id,student_homework_id) VALUES(3,10,1,100,4)', db, False).returncode, 0)
        self.sql('DELETE FROM student_homeworks WHERE id=4', db)
        self.assertEqual(self.value('SELECT student_homework_id IS NULL FROM clinic_homework_progress WHERE id=2', db), '1')
        self.assertEqual(self.value('SELECT COUNT(*) FROM student_homework_classes WHERE student_homework_id=4', db), '0')

    def assert_new_lesson_record_allowed(self, db):
        self.sql('''INSERT INTO student_homeworks(id,student_id,homework_id,lesson_id,incorrect_count) VALUES(4,1,100,2,0);
            INSERT INTO student_homework_classes VALUES(4,10);
            INSERT INTO clinic_homework_progress(id,clinic_id,student_id,homework_id,student_homework_id) VALUES(2,10,1,100,4);''', db)
        self.assertEqual(self.value('SELECT COUNT(*) FROM clinic_homework_progress WHERE clinic_id=10 AND student_id=1 AND homework_id=100', db), '2')

    def test_hibernate_baseline_without_legacy_clinic_index(self):
        db = self.database(legacy_clinic_index=False)
        self.sql(FORWARD, db)
        self.assert_new_lesson_record_allowed(db)

    def test_rollback_preserves_existing_records_and_restores_legacy_clinic_uniqueness(self):
        db = self.database()
        before = {table: self.value('SELECT ' + columns + ' FROM ' + table + ' ORDER BY id', db)
                  for table, columns in [('student_homeworks', PROGRESS_COLUMNS), ('clinic_homework_progress', CLINIC_COLUMNS)]}
        self.sql(FORWARD, db)
        self.sql(ROLLBACK, db)
        for table, columns in [('student_homeworks', PROGRESS_COLUMNS), ('clinic_homework_progress', CLINIC_COLUMNS)]:
            self.assertEqual(self.value('SELECT ' + columns + ' FROM ' + table + ' ORDER BY id', db), before[table])
        self.assertEqual(self.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('lesson_homeworks','student_homework_classes')", db), '0')
        self.assertEqual(self.value("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name IN ('assigned_homework_id','homework_assignment_set','lesson_scope_id','student_homework_id')", db), '0')
        self.assertEqual(self.value("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='clinic_homework_progress' AND index_name='uk_clinic_student_homework'", db), 'clinic_id,student_id,homework_id')

    def test_duplicate_student_homework_is_rejected_before_ddl(self):
        db = self.database()
        self.sql('INSERT INTO student_homeworks(id,student_id,homework_id) VALUES(4,2,200)', db)
        self.assert_rejected_before_ddl(FORWARD, db, 'ck_homework_migration_duplicate_progress')

    def test_multiple_homeworks_for_one_student_lesson_are_rejected_before_ddl(self):
        db = self.database()
        self.sql('INSERT INTO homeworks VALUES(400,10,1); INSERT INTO student_homeworks(id,student_id,homework_id) VALUES(4,1,400)', db)
        self.assert_rejected_before_ddl(FORWARD, db, 'ck_homework_migration_duplicate_assignments')

    def test_duplicate_clinic_snapshots_are_rejected_before_ddl(self):
        db = self.database(legacy_clinic_index=False)
        self.sql('INSERT INTO clinic_homework_progress(id,clinic_id,student_id,homework_id) VALUES(2,10,1,100)', db)
        self.assert_rejected_before_ddl(FORWARD, db, 'ck_homework_migration_duplicate_clinic')

    def test_partial_drafts_are_rejected_before_ddl(self):
        changes = [
            'CREATE TABLE lesson_homeworks(lesson_id BIGINT,homework_id BIGINT)',
            'ALTER TABLE student_lessons ADD COLUMN assigned_homework_id BIGINT',
            'ALTER TABLE student_homeworks ADD COLUMN lesson_id BIGINT',
            'ALTER TABLE clinic_homework_progress ADD COLUMN student_homework_id BIGINT',
            'ALTER TABLE student_homeworks ADD CONSTRAINT uk_student_homeworks_student_homework UNIQUE(student_id,homework_id)',
        ]
        for change in changes:
            with self.subTest(change=change):
                db = self.database()
                self.sql(change, db)
                self.assert_rejected_before_ddl(FORWARD, db, 'ck_homework_migration_existing_schema')

    def test_completed_migration_cannot_be_reapplied(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.assert_rejected_before_ddl(FORWARD, db, 'ck_homework_migration_existing_schema')

    def test_rollback_rejects_shared_lesson_links_before_ddl(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.sql('INSERT INTO lesson_homeworks VALUES(2,100)', db)
        self.assert_rejected_before_ddl(ROLLBACK, db, 'ck_homework_rollback_lesson_links')

    def test_rollback_rejects_a_new_progress_source_even_without_duplicate_records(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.sql('INSERT INTO homeworks VALUES(400,10,NULL); INSERT INTO student_homeworks(id,student_id,homework_id,lesson_id) VALUES(4,1,400,2)', db)
        self.assert_rejected_before_ddl(ROLLBACK, db, 'ck_homework_rollback_progress_sources')

    def test_rollback_rejects_explicit_unassignment_before_ddl(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.sql('UPDATE student_lessons SET assigned_homework_id=NULL WHERE student_id=1 AND lesson_id=1', db)
        self.assert_rejected_before_ddl(ROLLBACK, db, 'ck_homework_rollback_assignments')

    def test_rollback_rejects_extra_class_history_before_ddl(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.sql('INSERT INTO student_homework_classes VALUES(1,20)', db)
        self.assert_rejected_before_ddl(ROLLBACK, db, 'ck_homework_rollback_class_history')

    def test_rollback_rejects_clinic_snapshots_that_collide_under_the_legacy_key(self):
        db = self.database()
        self.sql(FORWARD, db)
        self.sql('INSERT INTO clinic_homework_progress(id,clinic_id,student_id,homework_id) VALUES(2,11,1,100)', db)
        self.assert_rejected_before_ddl(ROLLBACK, db, 'ck_homework_rollback_clinic')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True, help='Test-only MySQL 8.0.16+ container; root must have no password')
    args = parser.parse_args()
    HomeworkSharingMigrationTest.container = args.container
    unittest.main(argv=[__file__, '-v'])
