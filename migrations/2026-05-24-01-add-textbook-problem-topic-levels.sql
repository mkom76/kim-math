-- 교재 문제 유형(topic)을 5단계 계층으로 분해해서 보관.
--
-- topic 컬럼은 그대로 유지하되, 저장 시 백엔드가 `›` 구분자로 정규화한 path를
-- 넣는다 (예: "함수 › 일차함수 › 그래프"). topic_l1~topic_l5는 그 path를
-- 분해한 denormalize 컬럼으로, 레벨별 필터·자동완성·유형별 분석 group-by에
-- 사용한다.
--
-- 기존 단일 유형 데이터는 모두 L1로 들어간다 — 한 단계만 쓰던 선생님 시각엔
-- 변화 없음.

ALTER TABLE textbook_problems
    ADD COLUMN topic_l1 VARCHAR(100) NULL,
    ADD COLUMN topic_l2 VARCHAR(100) NULL,
    ADD COLUMN topic_l3 VARCHAR(100) NULL,
    ADD COLUMN topic_l4 VARCHAR(100) NULL,
    ADD COLUMN topic_l5 VARCHAR(100) NULL,
    ADD INDEX idx_textbook_problems_topic_l1 (topic_l1),
    ADD INDEX idx_textbook_problems_topic_l2 (topic_l2);

-- 기존 단일 topic을 L1로 그대로 백필.
UPDATE textbook_problems
   SET topic_l1 = topic
 WHERE topic IS NOT NULL
   AND topic <> '';
