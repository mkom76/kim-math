-- 학생 본인에게 시험 성적(총점/등수/반평균)을 숨기는 플래그.
-- 선생님 화면에는 영향 없음. 결과 화면의 유형별 분석/정답/해설은 그대로 노출.
ALTER TABLE students
    ADD COLUMN hide_scores_from_student BOOLEAN NOT NULL DEFAULT FALSE;
