-- Rollback for 2026-04-25-02-add-textbooks.sql

DROP TABLE IF EXISTS homework_problems;

ALTER TABLE test_questions DROP FOREIGN KEY fk_test_questions_textbook_problem;
DROP INDEX idx_test_questions_textbook_problem ON test_questions;
ALTER TABLE test_questions DROP COLUMN textbook_problem_id;

DROP TABLE IF EXISTS textbook_problems;
DROP TABLE IF EXISTS textbooks;
