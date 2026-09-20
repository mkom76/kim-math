CREATE TABLE student_account_merges (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id             BIGINT       NOT NULL,
    source_student_id      BIGINT       NOT NULL,
    target_student_id      BIGINT       NOT NULL,
    merged_by_teacher_id   BIGINT       NOT NULL,
    merged_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_account_merges_source UNIQUE (source_student_id),
    INDEX idx_student_account_merges_target (target_student_id),
    INDEX idx_student_account_merges_academy_time (academy_id, merged_at)
);
