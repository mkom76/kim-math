-- Migration: Add clinic_homework_progress table for tracking student homework completion changes
-- Date: 2026-01-17
-- Description: Track homework completion changes before and after clinic sessions

CREATE TABLE clinic_homework_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clinic_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,

    -- 클리닉 전 (시작 버튼 클릭 시점)
    incorrect_count_before INT NULL COMMENT '클리닉 전 오답 개수',
    unsolved_count_before INT NULL COMMENT '클리닉 전 미제출 개수',

    -- 클리닉 후 (종료 버튼 클릭 시점)
    incorrect_count_after INT NULL COMMENT '클리닉 후 오답 개수',
    unsolved_count_after INT NULL COMMENT '클리닉 후 미제출 개수',

    created_at DATETIME(6),
    updated_at DATETIME(6),

    PRIMARY KEY (id),
    CONSTRAINT uk_clinic_student_homework UNIQUE (clinic_id, student_id, homework_id),
    CONSTRAINT fk_clinic_progress_clinic FOREIGN KEY (clinic_id) REFERENCES clinics(id) ON DELETE CASCADE,
    CONSTRAINT fk_clinic_progress_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_clinic_progress_homework FOREIGN KEY (homework_id) REFERENCES homeworks(id) ON DELETE CASCADE,
    INDEX idx_clinic_progress_clinic (clinic_id),
    INDEX idx_clinic_progress_student (clinic_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
