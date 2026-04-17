-- Migration: Add question_type column to test_questions table
-- Date: 2026-02-02
-- Description: 시험 문제에 객관식/주관식 구분 필드 추가

-- Add question_type column with default value SUBJECTIVE
ALTER TABLE test_questions
ADD COLUMN question_type VARCHAR(20) NOT NULL DEFAULT 'SUBJECTIVE';

-- Update all existing questions to SUBJECTIVE (already done by DEFAULT, but explicit for clarity)
-- UPDATE test_questions SET question_type = 'SUBJECTIVE' WHERE question_type IS NULL;
