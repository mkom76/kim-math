-- 답하지 않은 시험 문항을 NULL로 저장하고 오답으로 채점할 수 있도록 허용한다.

ALTER TABLE student_submission_details
    MODIFY COLUMN student_answer VARCHAR(255) NULL;
