-- Run this once on databases that already applied 2026-06-20-auth-hardening.sql.
-- The original migration omitted the nullable change required by lazy hashing.

ALTER TABLE students
  MODIFY pin VARCHAR(4) NULL;
