CREATE TABLE student_ui_feedback (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    student_id     BIGINT       NOT NULL,
    academy_id     BIGINT       NOT NULL,
    sentiment      VARCHAR(24)  NOT NULL,
    category       VARCHAR(32)  NULL,
    message        VARCHAR(500) NULL,
    page_path      VARCHAR(255) NOT NULL,
    ui_version     VARCHAR(20)  NOT NULL,
    viewport_width INT          NULL,
    platform       VARCHAR(20)  NULL,
    app_version    VARCHAR(50)  NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_student_ui_feedback_student_created (student_id, created_at),
    INDEX idx_student_ui_feedback_academy_created (academy_id, created_at),
    CONSTRAINT fk_student_ui_feedback_student
        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_ui_feedback_academy
        FOREIGN KEY (academy_id) REFERENCES academies(id) ON DELETE CASCADE
);
