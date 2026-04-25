-- 교재(Textbook) 기능: 선생님 개인 소유 교재와 그 안의 문제, 시험/숙제와의 연결
--
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- MySQL 8 has no ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS guards.
-- Rollback via the *_rollback.sql sibling.

-- 1. 교재
CREATE TABLE textbooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    INDEX idx_textbooks_owner (owner_teacher_id),
    CONSTRAINT fk_textbooks_owner FOREIGN KEY (owner_teacher_id)
        REFERENCES teachers(id) ON DELETE RESTRICT
);

-- 2. 교재 문제
CREATE TABLE textbook_problems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    textbook_id BIGINT NOT NULL,
    number INT NOT NULL,
    answer VARCHAR(255) NULL,
    question_type VARCHAR(20) NOT NULL,
    topic VARCHAR(255) NULL,
    video_link VARCHAR(1024) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    INDEX idx_textbook_problems_textbook (textbook_id),
    INDEX idx_textbook_problems_topic (topic),
    CONSTRAINT fk_textbook_problems_textbook FOREIGN KEY (textbook_id)
        REFERENCES textbooks(id) ON DELETE CASCADE
);

-- 3. test_questions에 교재 출처 FK 추가 (라이브 메타데이터 조회용)
ALTER TABLE test_questions
    ADD COLUMN textbook_problem_id BIGINT NULL,
    ADD CONSTRAINT fk_test_questions_textbook_problem FOREIGN KEY (textbook_problem_id)
        REFERENCES textbook_problems(id) ON DELETE SET NULL;

CREATE INDEX idx_test_questions_textbook_problem ON test_questions (textbook_problem_id);

-- 4. 숙제-문제 조인 테이블 (수동 슬롯과 교재 참조 혼합 가능)
CREATE TABLE homework_problems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT NOT NULL,
    textbook_problem_id BIGINT NULL,
    position INT NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    INDEX idx_homework_problems_homework (homework_id),
    INDEX idx_homework_problems_textbook_problem (textbook_problem_id),
    UNIQUE KEY uq_homework_problems_position (homework_id, position),
    CONSTRAINT fk_homework_problems_homework FOREIGN KEY (homework_id)
        REFERENCES homeworks(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_problems_textbook_problem FOREIGN KEY (textbook_problem_id)
        REFERENCES textbook_problems(id) ON DELETE SET NULL
);
