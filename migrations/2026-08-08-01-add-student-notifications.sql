CREATE TABLE student_notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    student_id  BIGINT       NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    body        VARCHAR(500) NOT NULL,
    target_path VARCHAR(255) NULL,
    source_key  VARCHAR(120) NULL,
    read_at     TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_student_notifications_source (student_id, source_key),
    INDEX idx_student_notifications_inbox (student_id, created_at),
    INDEX idx_student_notifications_unread (student_id, read_at),
    CONSTRAINT fk_student_notifications_student
        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);
