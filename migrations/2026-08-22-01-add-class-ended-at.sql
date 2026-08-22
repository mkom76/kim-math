-- 반을 삭제하지 않고 종강 상태로 전환해 학생·수업·성적 이력을 보존한다.

ALTER TABLE academy_classes
    ADD COLUMN ended_at DATETIME(6) NULL,
    ADD INDEX idx_academy_classes_academy_ended (academy_id, ended_at);
