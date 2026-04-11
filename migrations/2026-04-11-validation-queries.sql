-- 마이그레이션 후 정합성 검증 쿼리
-- =====================================================================
-- 각 쿼리는 0행을 반환해야 정상.
-- 실행 시점: Phase 2 백필 후, Phase 4 NOT NULL 적용 전, 그리고 정기 점검 시.
-- =====================================================================

-- 1) 멤버십이 0개인 학원 (적어도 어드민 1명은 있어야 함)
SELECT a.id, a.name AS academy_name
FROM academies a
LEFT JOIN teacher_academies ta ON ta.academy_id = a.id
WHERE ta.id IS NULL;

-- 2) 어드민이 없는 학원
SELECT a.id, a.name AS academy_name
FROM academies a
WHERE NOT EXISTS (
  SELECT 1 FROM teacher_academies ta
  WHERE ta.academy_id = a.id AND ta.role = 'ACADEMY_ADMIN'
);

-- 3) owner가 없는 반 (Phase 4 적용 전 반드시 0이어야 함)
SELECT id, name, academy_id
FROM academy_classes
WHERE owner_teacher_id IS NULL;

-- 4) owner인데 그 학원의 멤버가 아닌 케이스 (정합성 위반 — 데이터 누출 위험)
SELECT c.id, c.name AS class_name, c.owner_teacher_id, c.academy_id
FROM academy_classes c
WHERE c.owner_teacher_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM teacher_academies ta
    WHERE ta.teacher_id = c.owner_teacher_id
      AND ta.academy_id = c.academy_id
  );

-- 5) 같은 (teacher_id, academy_id) 멤버십이 중복된 케이스 (UK 제약이 있어서 발생할 수 없지만 안전 차원)
SELECT teacher_id, academy_id, COUNT(*) AS dup_count
FROM teacher_academies
GROUP BY teacher_id, academy_id
HAVING COUNT(*) > 1;

-- 6) 고아 멤버십 — 더 이상 존재하지 않는 teacher 또는 academy를 가리키는 row
SELECT ta.id, ta.teacher_id, ta.academy_id
FROM teacher_academies ta
LEFT JOIN teachers t ON t.id = ta.teacher_id
LEFT JOIN academies a ON a.id = ta.academy_id
WHERE t.id IS NULL OR a.id IS NULL;
