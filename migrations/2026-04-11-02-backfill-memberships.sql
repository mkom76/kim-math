-- Phase 2: 기존 데이터 백필
-- =====================================================================
-- ⚠ ONE-SHOT MIGRATION — DO NOT RE-RUN
-- 사용자가 :seedAdminTeacherId를 적절한 값으로 치환 후 실행
-- 적용 시점: Phase 1 적용 후, Phase 3(코드 배포) 직전
--
-- 롤백:
--   TRUNCATE teacher_academies;
--   UPDATE academy_classes SET owner_teacher_id = NULL;
-- =====================================================================

SET @seedAdminTeacherId = 1;  -- ⚠ 운영자가 변경: 어드민으로 승격할 teacher ID

-- 1) 기존 선생님 × 학원 곱집합으로 멤버십 생성
--    (현재 격리가 없으므로 사실상 모두 접근 가능했던 상태를 반영)
INSERT INTO teacher_academies (teacher_id, academy_id, role, created_at, updated_at)
SELECT t.id, a.id,
       CASE WHEN t.id = @seedAdminTeacherId THEN 'ACADEMY_ADMIN' ELSE 'TEACHER' END,
       NOW(6), NOW(6)
FROM teachers t CROSS JOIN academies a;

-- 2) 모든 기존 반의 owner를 어드민으로 일괄 지정
UPDATE academy_classes
   SET owner_teacher_id = @seedAdminTeacherId
 WHERE owner_teacher_id IS NULL;

-- 3) 인라인 검증 (참고용)
SELECT 'memberships count' AS metric, COUNT(*) AS value FROM teacher_academies
UNION ALL
SELECT 'classes without owner', COUNT(*) FROM academy_classes WHERE owner_teacher_id IS NULL
UNION ALL
SELECT 'admin count', COUNT(*) FROM teacher_academies WHERE role = 'ACADEMY_ADMIN';
