-- 롤백: question_type을 다시 NOT NULL로. 만약 NULL 데이터가 있으면 SUBJECTIVE로 백필.
UPDATE textbook_problems SET question_type = 'SUBJECTIVE' WHERE question_type IS NULL;
ALTER TABLE textbook_problems
    MODIFY COLUMN question_type VARCHAR(20) NOT NULL;
