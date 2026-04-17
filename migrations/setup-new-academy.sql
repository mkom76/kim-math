-- =====================================================================
-- 새 학원 셋업 (DB 직접 조작용 템플릿)
-- =====================================================================
-- 현재 앱은 어드민 권한으로 새 학원을 생성하는 정식 흐름이 없음.
-- (POST /api/academies는 학원 row만 만들 뿐, 새 학원의 멤버십을 만드는
--  경로가 없어서 실질적으로 사용 불가.)
-- 따라서 학원 + 어드민 셋업은 이 SQL로 직접 처리한다.
--
-- 사용법:
--   1. 아래 시나리오 중 필요한 블록 1개만 선택
--   2. @변수들을 적절한 값으로 변경
--   3. mysql -u root -p <db_name> < migrations/setup-new-academy.sql
--   4. 끝나면 마이그레이션 검증 쿼리 실행:
--      mysql -u root -p <db_name> < migrations/2026-04-11-validation-queries.sql
--
-- ⚠ 주의:
--   - 로컬(local 프로필)은 ddl-auto=create-drop이라 앱 재시작 시 데이터가
--     날아간다. 로컬 영구 보존이 필요하면 DataInitializer.java에 추가하거나
--     application.yml의 ddl-auto를 update로 변경할 것.
--   - teacher_academies(teacher_id, academy_id)에 unique 제약이 있다.
--     중복 INSERT 시 실패하므로 사전 체크를 권장.
-- =====================================================================


-- =====================================================================
-- 시나리오 A: 새 학원 + 기존 선생님을 어드민으로 임명
-- =====================================================================
-- 이미 시스템에 등록된 선생님을 새 학원의 어드민으로 만든다.

START TRANSACTION;

-- A-1) 어드민으로 임명할 기존 선생님 ID 확인 (먼저 SELECT로 확인)
SELECT id, name, username FROM teachers WHERE username = 'kim';
-- → 결과를 보고 아래 @adminTeacherId를 변경

SET @newAcademyName  = '새로운수학학원';   -- ⚠ 학원 이름
SET @adminTeacherId  = 2;                  -- ⚠ 위 SELECT 결과의 teacher.id

-- A-2) 학원 생성
INSERT INTO academies (name, created_at, updated_at)
VALUES (@newAcademyName, NOW(6), NOW(6));

SET @newAcademyId = LAST_INSERT_ID();

-- A-3) 멤버십 생성 (어드민 역할)
INSERT INTO teacher_academies (teacher_id, academy_id, role, created_at, updated_at)
VALUES (@adminTeacherId, @newAcademyId, 'ACADEMY_ADMIN', NOW(6), NOW(6));

-- A-4) 검증
SELECT a.id   AS academy_id,
       a.name AS academy_name,
       t.id   AS teacher_id,
       t.name AS teacher_name,
       t.username,
       ta.role
  FROM academies a
  JOIN teacher_academies ta ON ta.academy_id = a.id
  JOIN teachers t           ON t.id          = ta.teacher_id
 WHERE a.id = @newAcademyId;

COMMIT;


-- =====================================================================
-- 시나리오 B: 새 학원 + 새 어드민 선생님까지 같이 생성
-- =====================================================================
-- 학원도 새고 어드민도 새로 만드는 경우.
-- 시나리오 A를 사용한다면 아래 블록은 주석 처리하거나 건너뛸 것.

START TRANSACTION;

SET @newAcademyName  = '압구정수학학원';   -- ⚠ 학원 이름
SET @newAdminName    = '정원장';           -- ⚠ 선생님 이름
SET @newAdminUser    = 'jung_admin';       -- ⚠ username (전역 unique, 중복 불가)
SET @newAdminPin     = '999999';           -- ⚠ 6자리 PIN (앱 폼 maxlength=6)

-- B-1) username 중복 사전 체크 (0행이어야 함)
SELECT id, username FROM teachers WHERE username = @newAdminUser;
-- → 1행 이상 나오면 다른 username 사용. 강제 진행 금지.

-- B-2) 학원 생성
INSERT INTO academies (name, created_at, updated_at)
VALUES (@newAcademyName, NOW(6), NOW(6));

SET @newAcademyId = LAST_INSERT_ID();

-- B-3) 어드민 선생님 생성
INSERT INTO teachers (name, username, pin, created_at, updated_at)
VALUES (@newAdminName, @newAdminUser, @newAdminPin, NOW(6), NOW(6));

SET @newAdminId = LAST_INSERT_ID();

-- B-4) 멤버십 생성 (어드민 역할)
INSERT INTO teacher_academies (teacher_id, academy_id, role, created_at, updated_at)
VALUES (@newAdminId, @newAcademyId, 'ACADEMY_ADMIN', NOW(6), NOW(6));

-- B-5) 검증
SELECT a.id   AS academy_id,
       a.name AS academy_name,
       t.id   AS teacher_id,
       t.name AS teacher_name,
       t.username,
       ta.role
  FROM academies a
  JOIN teacher_academies ta ON ta.academy_id = a.id
  JOIN teachers t           ON t.id          = ta.teacher_id
 WHERE a.id = @newAcademyId;

COMMIT;


-- =====================================================================
-- 시나리오 C: 기존 선생님에게 추가 학원 멤버십 부여 (멀티 학원)
-- =====================================================================
-- 학원과 선생님 모두 이미 있고, 선생님을 다른 학원의 멤버로 추가.
-- 멀티 학원 케이스 (학원 전환 테스트용으로도 유용).

START TRANSACTION;

-- C-1) 대상 확인
SELECT id, username, name FROM teachers WHERE username = 'lee';
SELECT id, name           FROM academies WHERE name = '대치수학학원';

SET @teacherId       = 3;            -- ⚠ 위 SELECT 결과의 teacher.id
SET @academyId       = 2;            -- ⚠ 위 SELECT 결과의 academy.id
SET @membershipRole  = 'TEACHER';    -- 'TEACHER' 또는 'ACADEMY_ADMIN'

-- C-2) 이미 멤버십이 있는지 사전 체크 (0행이어야 함)
SELECT * FROM teacher_academies
 WHERE teacher_id = @teacherId AND academy_id = @academyId;
-- → 1행 이상 나오면 이미 멤버. UNIQUE 제약(uk_teacher_academy) 때문에
--   INSERT가 실패한다. 진행 금지.

-- C-3) 멤버십 추가
INSERT INTO teacher_academies (teacher_id, academy_id, role, created_at, updated_at)
VALUES (@teacherId, @academyId, @membershipRole, NOW(6), NOW(6));

-- C-4) 검증
SELECT t.id        AS teacher_id,
       t.name      AS teacher_name,
       t.username,
       a.id        AS academy_id,
       a.name      AS academy_name,
       ta.role
  FROM teacher_academies ta
  JOIN teachers t  ON t.id = ta.teacher_id
  JOIN academies a ON a.id = ta.academy_id
 WHERE ta.teacher_id = @teacherId
 ORDER BY a.id;

COMMIT;


-- =====================================================================
-- 셋업 후 사용 흐름
-- =====================================================================
-- 1. 어드민 username/PIN으로 앱 로그인
-- 2. 헤더 드롭다운에 새 학원이 노출되는지 확인
-- 3. 새 학원으로 컨텍스트 전환 (자동 또는 수동)
-- 4. /academy-classes 페이지에서 반 생성
--    → AcademyClassService.createClass가 자동으로 owner를 본인으로 지정
-- 5. /admin/teachers 페이지에서 다른 선생님 초대
-- 6. /admin/class-owners 페이지에서 반 owner 재지정 가능
--
-- 정합성 검증:
--   mysql -u root -p <db_name> < migrations/2026-04-11-validation-queries.sql
--   → 6개 쿼리 모두 0행 반환이 정상
-- =====================================================================
