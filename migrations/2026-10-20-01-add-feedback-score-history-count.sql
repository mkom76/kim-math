-- Apply before deploying the score-history feedback setting.
ALTER TABLE feedback_prompt_templates
    ADD COLUMN score_history_count INT NOT NULL DEFAULT 3;

SHOW COLUMNS FROM feedback_prompt_templates LIKE 'score_history_count';
SELECT COUNT(*) AS invalid_score_history_count
FROM feedback_prompt_templates
WHERE score_history_count IS NULL OR score_history_count NOT BETWEEN 0 AND 10;
