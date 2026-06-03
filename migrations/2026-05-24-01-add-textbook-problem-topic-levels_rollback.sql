ALTER TABLE textbook_problems
    DROP INDEX idx_textbook_problems_topic_l2,
    DROP INDEX idx_textbook_problems_topic_l1,
    DROP COLUMN topic_l5,
    DROP COLUMN topic_l4,
    DROP COLUMN topic_l3,
    DROP COLUMN topic_l2,
    DROP COLUMN topic_l1;
