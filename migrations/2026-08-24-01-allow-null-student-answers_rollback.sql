-- NOT NULL 복원 전에 기존 미입력 답안을 빈 문자열로 백필한다.

UPDATE student_submission_details
SET student_answer = ''
WHERE student_answer IS NULL;

ALTER TABLE student_submission_details
    MODIFY COLUMN student_answer VARCHAR(255) NOT NULL;
