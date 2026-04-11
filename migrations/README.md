# Migrations Runbook

이 디렉토리의 SQL은 운영자가 직접 실행하는 일회성 마이그레이션입니다. 자동 마이그레이션 도구(예: Flyway, Liquibase)는 사용하지 않습니다.

## 2026-04-11: 선생님/학원 격리 마이그레이션

### 배경

선생님/학원 단위 멀티테넌트 격리 도입. 학원 어드민은 자기 학원 전체, 일반 선생님은 자기가 owner인 반만 접근. 한 선생님이 여러 학원에 소속될 수 있음.

설계 문서: `docs/plans/2026-04-11-teacher-isolation-design.md`
구현 계획: `docs/plans/2026-04-11-teacher-isolation-implementation.md`

### 전체 적용 순서

```
[Phase 1] 스키마 추가     ─ 코드 배포 전
[Phase 2] 데이터 백필     ─ Phase 1 직후
[Phase 3] 코드 배포       ─ Phase 2 직후 (브랜치 머지)
[Phase 4] NOT NULL 강화   ─ Phase 3 안정화 후 (수일~1주)
```

각 Phase는 독립 배포 가능. 롤백 가능 시점도 Phase 단위.

---

### Phase 1: 스키마 추가

**파일**: `2026-04-11-01-add-teacher-academies.sql`

```bash
mysql -u root -p academy < migrations/2026-04-11-01-add-teacher-academies.sql
```

이 단계까지는 코드가 새 컬럼/테이블을 사용하지 않으므로 안전.

**검증** (검증 쿼리는 이 시점에서는 모두 0행이 정상이 아님 — 백필 후에 의미 있음):
```sql
DESCRIBE teacher_academies;
SHOW CREATE TABLE academy_classes \G  -- owner_teacher_id 컬럼 확인
```

**롤백**:
```sql
DROP TABLE IF EXISTS teacher_academies;
ALTER TABLE academy_classes DROP FOREIGN KEY fk_class_owner;
ALTER TABLE academy_classes DROP COLUMN owner_teacher_id;
```

⚠ **주의**: 이 마이그레이션은 ONE-SHOT입니다. 부분 실패 시 위 cleanup 블록을 먼저 실행한 후 재시도.

---

### Phase 2: 데이터 백필

**파일**: `2026-04-11-02-backfill-memberships.sql`

**사전 작업**: 파일을 열어 `@seedAdminTeacherId`를 어드민으로 승격할 선생님의 ID로 변경.

```bash
# 1. 어드민 후보 확인
mysql -u root -p academy -e "SELECT id, name, username FROM teachers ORDER BY id;"

# 2. 파일 편집
# 예: SET @seedAdminTeacherId = 7;  -- 김원장 선생님

# 3. 적용
mysql -u root -p academy < migrations/2026-04-11-02-backfill-memberships.sql
```

이 마이그레이션은:
- 모든 (선생님 × 학원) 조합으로 멤버십 row 생성
- `@seedAdminTeacherId`로 지정된 선생님은 모든 학원에서 `ACADEMY_ADMIN`, 나머지는 `TEACHER`
- 모든 기존 반의 `owner_teacher_id`를 어드민으로 일괄 지정

배포 후 어드민이 로그인하여 반의 owner를 적절한 선생님에게 재지정해야 함 (어드민 UI: `/admin/class-owners`).

**검증**:
```bash
mysql -u root -p academy < migrations/2026-04-11-validation-queries.sql
```
6개 쿼리 모두 0행 반환이 정상. 0행이 아니면 해당 항목 정리 후 진행.

**롤백**:
```sql
TRUNCATE teacher_academies;
UPDATE academy_classes SET owner_teacher_id = NULL;
```

---

### Phase 3: 코드 배포

`feature/teacher-isolation` 브랜치를 `main`에 머지하고 배포.

이 시점부터 격리가 실제로 동작:
- 선생님 로그인 시 멤버십 응답
- 헤더의 학원 드롭다운으로 컨텍스트 전환
- 어드민은 `/admin/teachers`, `/admin/class-owners` 사용 가능
- 일반 선생님은 자기가 owner인 반의 데이터만 노출

**배포 직전 체크**:
- [ ] Phase 1, 2 적용 완료
- [ ] 검증 쿼리 6개 모두 0행
- [ ] 어드민으로 로그인 가능 확인 (스테이징)

**롤백**: 코드 revert. 스키마는 그대로 둬도 무해.

---

### Phase 4: NOT NULL 강화

**파일**: `2026-04-11-03-enforce-not-null.sql`

Phase 3 배포 후 며칠~1주 정도 운영해 새 데이터가 안정적으로 owner를 가지는지 확인한 다음 적용.

**사전 체크 (반드시 0이어야 함)**:
```sql
SELECT COUNT(*) FROM academy_classes WHERE owner_teacher_id IS NULL;
```

```bash
mysql -u root -p academy < migrations/2026-04-11-03-enforce-not-null.sql
```

**롤백**:
```sql
ALTER TABLE academy_classes MODIFY COLUMN owner_teacher_id BIGINT NULL;
```

---

## 검증 쿼리 사용 시점

`2026-04-11-validation-queries.sql`는 다음 시점에 실행:
- Phase 2 백필 직후
- Phase 4 NOT NULL 적용 직전
- 정기 점검 (월 1회 권장)

각 쿼리가 검사하는 항목:
1. 멤버십이 0개인 학원 (어드민이라도 1명은 있어야 함)
2. 어드민이 0명인 학원
3. owner가 NULL인 반 (Phase 4 전 0이어야 함)
4. owner인데 그 학원의 멤버가 아닌 반 (정합성 위반 — 위험)
5. 중복 멤버십 (UK 제약으로 발생 불가지만 안전 차원)
6. 고아 멤버십 (존재하지 않는 teacher/academy 참조)

모든 쿼리가 0행 반환이 정상.

---

## 운영 시 주의사항

- **어드민이 owner인 반을 다른 선생님에게 위임할 때**: `/admin/class-owners` 페이지 사용. 새 owner는 반드시 같은 학원의 멤버여야 함 (앱 레벨 검증).
- **선생님 멤버십 제거 시**: 그 선생님이 owner였던 반은 자동으로 어드민(요청자)에게 이관됨. 사후에 적절한 다른 선생님에게 재지정 필요.
- **본인 멤버십 제거 / 본인을 마지막 어드민에서 강등**: 앱 레벨에서 차단됨.
- **`spring.jpa.open-in-view=true`** 가 반드시 켜져 있어야 함. 꺼지면 `TenantFilterStartupCheck`가 앱 시작을 거부함.
