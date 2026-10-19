-- Run only after reverting the application; saved history-count settings are lost.
ALTER TABLE feedback_prompt_templates DROP COLUMN score_history_count;
