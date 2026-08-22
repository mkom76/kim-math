-- 객관식 문항이 여러 보기를 정답으로 요구하는지 저장한다.

ALTER TABLE test_questions
    ADD COLUMN multiple_answers BOOLEAN NOT NULL DEFAULT FALSE AFTER answer;
