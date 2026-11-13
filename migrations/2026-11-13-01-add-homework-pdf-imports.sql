-- Developer-approved design; execute only in the explicitly approved DB.
-- MySQL 8.0.16+. Back up first. Stop on error; do not rerun or use --force.
-- Existing homework, question and student progress records are not rewritten.
CREATE TABLE homework_pdf_imports (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    homework_id BIGINT NULL,
    state VARCHAR(20) NOT NULL,
    storage_key VARCHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    page_count INT NOT NULL,
    subject_id VARCHAR(32) NOT NULL,
    catalog_version VARCHAR(64) NOT NULL,
    ai_model VARCHAR(80) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    registration_hash VARCHAR(64) NULL,
    error_code VARCHAR(40) NULL,
    review_required BIT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_pdf_import_request UNIQUE (teacher_id, request_key),
    CONSTRAINT uk_pdf_import_homework UNIQUE (homework_id),
    CONSTRAINT fk_pdf_import_academy FOREIGN KEY (academy_id) REFERENCES academies(id),
    CONSTRAINT fk_pdf_import_class FOREIGN KEY (class_id) REFERENCES academy_classes(id),
    CONSTRAINT fk_pdf_import_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id),
    CONSTRAINT fk_pdf_import_homework FOREIGN KEY (homework_id) REFERENCES homeworks(id),
    CONSTRAINT ck_pdf_import_size CHECK (file_size BETWEEN 5 AND 8388608),
    CONSTRAINT ck_pdf_import_pages CHECK (page_count BETWEEN 1 AND 20),
    INDEX ix_pdf_import_cleanup (state, expires_at),
    INDEX ix_pdf_import_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE homework_pdf_questions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    import_id VARCHAR(36) NOT NULL,
    homework_problem_id BIGINT NULL,
    position INT NOT NULL,
    source_number VARCHAR(40) NOT NULL,
    regions_json TEXT NOT NULL,
    extracted_text LONGTEXT NOT NULL,
    suggested_type_id VARCHAR(64) NULL,
    confirmed_type_id VARCHAR(64) NULL,
    type_path_snapshot TEXT NULL,
    review_state VARCHAR(24) NOT NULL,
    classification_reason VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_pdf_question_position UNIQUE (import_id, position),
    CONSTRAINT uk_pdf_question_slot UNIQUE (homework_problem_id),
    CONSTRAINT fk_pdf_question_import FOREIGN KEY (import_id) REFERENCES homework_pdf_imports(id),
    CONSTRAINT fk_pdf_question_slot FOREIGN KEY (homework_problem_id) REFERENCES homework_problems(id),
    CONSTRAINT ck_pdf_question_position CHECK (position BETWEEN 1 AND 60)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS existing_homework_count FROM homeworks;
SELECT COUNT(*) AS existing_progress_count FROM student_homeworks;
SELECT COUNT(*) AS new_import_count FROM homework_pdf_imports;
SELECT COUNT(*) AS new_pdf_question_count FROM homework_pdf_questions;
