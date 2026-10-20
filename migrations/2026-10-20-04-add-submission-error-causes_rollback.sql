-- 이 기능을 사용하기 전의 애플리케이션으로 되돌린 후 적용한다. 저장된 원인 기록은 삭제된다.
ALTER TABLE student_submissions
    DROP CHECK chk_submission_error_causes_nonnegative,
    DROP COLUMN error_cause_calculation_mistake,
    DROP COLUMN error_cause_concept_gap,
    DROP COLUMN error_cause_condition_misread,
    DROP COLUMN error_cause_idea_gap,
    DROP COLUMN error_cause_time_management,
    DROP COLUMN error_causes_need_review;
