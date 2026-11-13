-- Developer-approved design; execute only in the explicitly approved DB.
-- MySQL 8.0.16+. Back up first. Stop on error; do not rerun or use --force.
-- Existing exams, submissions, grades and homework records are not rewritten.
CREATE TABLE exam_pdf_imports (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    test_id BIGINT NULL,
    state VARCHAR(20) NOT NULL,
    storage_key VARCHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    page_count INT NOT NULL,
    question_page_count INT NOT NULL,
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
    CONSTRAINT uk_exam_pdf_import_request UNIQUE (teacher_id, request_key),
    CONSTRAINT uk_exam_pdf_import_test UNIQUE (test_id),
    CONSTRAINT fk_exam_pdf_import_academy FOREIGN KEY (academy_id) REFERENCES academies(id),
    CONSTRAINT fk_exam_pdf_import_class FOREIGN KEY (class_id) REFERENCES academy_classes(id),
    CONSTRAINT fk_exam_pdf_import_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id),
    CONSTRAINT fk_exam_pdf_import_test FOREIGN KEY (test_id) REFERENCES tests(id),
    CONSTRAINT ck_exam_pdf_import_size CHECK (file_size BETWEEN 5 AND 8388608),
    CONSTRAINT ck_exam_pdf_import_pages CHECK (page_count BETWEEN 1 AND 20),
    CONSTRAINT ck_exam_pdf_question_pages CHECK (question_page_count >= 1 AND question_page_count < page_count),
    INDEX ix_exam_pdf_import_cleanup (state, expires_at),
    INDEX ix_exam_pdf_import_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exam_pdf_questions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    import_id VARCHAR(36) NOT NULL,
    test_question_id BIGINT NULL,
    position INT NOT NULL,
    source_number VARCHAR(40) NOT NULL,
    regions_json TEXT NOT NULL,
    extracted_text LONGTEXT NOT NULL,
    suggested_type_id VARCHAR(64) NULL,
    confirmed_type_id VARCHAR(64) NULL,
    type_path_snapshot TEXT NULL,
    review_state VARCHAR(24) NOT NULL,
    classification_reason VARCHAR(500) NOT NULL,
    answer TEXT NULL,
    points DOUBLE NULL,
    format VARCHAR(24) NULL,
    multiple_answers BIT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_exam_pdf_question_position UNIQUE (import_id, position),
    CONSTRAINT uk_exam_pdf_question_slot UNIQUE (test_question_id),
    CONSTRAINT fk_exam_pdf_question_import FOREIGN KEY (import_id) REFERENCES exam_pdf_imports(id),
    CONSTRAINT fk_exam_pdf_question_slot FOREIGN KEY (test_question_id) REFERENCES test_questions(id),
    CONSTRAINT ck_exam_pdf_question_position CHECK (position BETWEEN 1 AND 60)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS existing_tests FROM tests;
SELECT COUNT(*) AS existing_submissions FROM student_submissions;
SELECT COUNT(*) AS new_exam_imports FROM exam_pdf_imports;
SELECT COUNT(*) AS new_exam_questions FROM exam_pdf_questions;
