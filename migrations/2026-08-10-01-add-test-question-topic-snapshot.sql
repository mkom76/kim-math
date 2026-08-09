-- 시험 문항에 수학 유형(topic) 스냅샷을 보관한다.
-- 교재 문항에서 출제한 경우 현재 교재 유형을 백필하고, 이후에는 시험별로 독립 편집한다.

ALTER TABLE test_questions
    ADD COLUMN topic VARCHAR(512) NULL,
    ADD COLUMN topic_l1 VARCHAR(100) NULL,
    ADD COLUMN topic_l2 VARCHAR(100) NULL,
    ADD COLUMN topic_l3 VARCHAR(100) NULL,
    ADD COLUMN topic_l4 VARCHAR(100) NULL,
    ADD COLUMN topic_l5 VARCHAR(100) NULL,
    ADD INDEX idx_test_questions_topic_l1 (topic_l1),
    ADD INDEX idx_test_questions_topic_l2 (topic_l2);

UPDATE test_questions tq
JOIN textbook_problems tp ON tp.id = tq.textbook_problem_id
SET tq.topic = tp.topic,
    tq.topic_l1 = tp.topic_l1,
    tq.topic_l2 = tp.topic_l2,
    tq.topic_l3 = tp.topic_l3,
    tq.topic_l4 = tp.topic_l4,
    tq.topic_l5 = tp.topic_l5
WHERE tp.topic IS NOT NULL
  AND tp.topic <> '';
