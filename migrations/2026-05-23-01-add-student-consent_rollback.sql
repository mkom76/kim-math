DROP TABLE IF EXISTS student_consents;

ALTER TABLE students
    DROP COLUMN status,
    DROP COLUMN contact_phone,
    DROP COLUMN parent_phone,
    DROP COLUMN parent_name;
