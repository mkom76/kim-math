-- 원인 건수의 NULL은 미입력, 0은 해당 원인 없음. 원인 간 중복 기록을 허용한다.
ALTER TABLE student_submissions
    ADD COLUMN error_cause_calculation_mistake INT NULL,
    ADD COLUMN error_cause_concept_gap INT NULL,
    ADD COLUMN error_cause_condition_misread INT NULL,
    ADD COLUMN error_cause_idea_gap INT NULL,
    ADD COLUMN error_cause_time_management INT NULL,
    ADD COLUMN error_causes_need_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_submission_error_causes_nonnegative CHECK (
        (error_cause_calculation_mistake IS NULL OR error_cause_calculation_mistake >= 0)
        AND (error_cause_concept_gap IS NULL OR error_cause_concept_gap >= 0)
        AND (error_cause_condition_misread IS NULL OR error_cause_condition_misread >= 0)
        AND (error_cause_idea_gap IS NULL OR error_cause_idea_gap >= 0)
        AND (error_cause_time_management IS NULL OR error_cause_time_management >= 0)
    );
