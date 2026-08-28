-- 학생 계정과 반 소속을 분리한다. students.class_id는 전환 기간 동안 호환 컬럼으로 유지한다.
-- 기존 학생은 현재 반의 종강 여부에 따라 ACTIVE 또는 COMPLETED 소속으로 백필한다.
--
-- 적용 순서: 이 파일 실행 -> 검증 쿼리 확인 -> 애플리케이션 배포

CREATE TABLE student_class_enrollments (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    student_id            BIGINT       NOT NULL,
    class_id              BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    started_at            DATETIME(6)  NOT NULL,
    ended_at              DATETIME(6)  NULL,
    end_reason            VARCHAR(200) NULL,
    created_by_teacher_id BIGINT       NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_class_enrollment UNIQUE (student_id, class_id),
    INDEX idx_student_enrollment_status (student_id, status, started_at),
    INDEX idx_class_enrollment_status (class_id, status),
    CONSTRAINT fk_student_class_enrollment_student
        FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_class_enrollment_class
        FOREIGN KEY (class_id) REFERENCES academy_classes(id),
    CONSTRAINT fk_student_class_enrollment_creator
        FOREIGN KEY (created_by_teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
    CONSTRAINT chk_student_class_enrollment_status
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'WITHDRAWN'))
);

INSERT INTO student_class_enrollments (
    student_id,
    class_id,
    status,
    started_at,
    ended_at,
    end_reason,
    created_at,
    updated_at
)
SELECT
    s.id,
    s.class_id,
    CASE WHEN ac.ended_at IS NULL THEN 'ACTIVE' ELSE 'COMPLETED' END,
    COALESCE(s.created_at, CURRENT_TIMESTAMP(6)),
    ac.ended_at,
    CASE WHEN ac.ended_at IS NULL THEN NULL ELSE 'CLASS_ENDED' END,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM students s
JOIN academy_classes ac ON ac.id = s.class_id;

-- 아래 두 결과가 모두 0이어야 한다.
SELECT COUNT(*) AS students_without_enrollment
FROM students s
LEFT JOIN student_class_enrollments sce ON sce.student_id = s.id
WHERE sce.id IS NULL;

SELECT COUNT(*) AS cross_academy_enrollments
FROM student_class_enrollments sce
JOIN students s ON s.id = sce.student_id
JOIN academy_classes ac ON ac.id = sce.class_id
WHERE s.academy_id <> ac.academy_id;
