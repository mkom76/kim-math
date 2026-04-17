-- 숙제 문항번호 저장 필드 추가
ALTER TABLE student_homeworks ADD COLUMN incorrect_questions VARCHAR(500) DEFAULT NULL;
ALTER TABLE student_homeworks ADD COLUMN unsolved_questions VARCHAR(500) DEFAULT NULL;
