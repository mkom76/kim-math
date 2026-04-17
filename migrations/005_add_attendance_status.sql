-- 출석 상태 컬럼 추가
ALTER TABLE student_lessons ADD COLUMN attendance_status VARCHAR(20) DEFAULT NULL;
