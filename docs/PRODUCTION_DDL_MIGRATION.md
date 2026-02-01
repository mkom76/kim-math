# 프로덕션 데이터베이스 마이그레이션 가이드

## 배포 전 체크리스트

- [ ] 프로덕션 데이터베이스 백업 완료
- [ ] 아래 DDL 스크립트 검토 완료
- [ ] Flyway 마이그레이션 버전 확인
- [ ] 롤백 계획 수립

---

## 적용해야 할 DDL (순서대로 실행)

### 1. V6: lesson_videos 테이블 생성

**목적:** 수업 영상 관리 기능

```sql
CREATE TABLE lesson_videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    youtube_url VARCHAR(500) NOT NULL,
    youtube_video_id VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    thumbnail_url VARCHAR(500),
    duration VARCHAR(20),
    order_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    INDEX idx_lesson_id (lesson_id),
    INDEX idx_order (lesson_id, order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**검증:**
```sql
-- 테이블 생성 확인
SHOW CREATE TABLE lesson_videos;

-- 인덱스 확인
SHOW INDEX FROM lesson_videos;
```

---

### 2. V7: student_video_progress 테이블 생성

**목적:** 학생 영상 시청 진행률 추적 기능

```sql
CREATE TABLE student_video_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_time INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_watched_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (lesson_video_id) REFERENCES lesson_videos(id) ON DELETE CASCADE,

    UNIQUE KEY uk_student_video (student_id, lesson_video_id),
    INDEX idx_student_id (student_id),
    INDEX idx_lesson_video_id (lesson_video_id),
    INDEX idx_completed (completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**검증:**
```sql
-- 테이블 생성 확인
SHOW CREATE TABLE student_video_progress;

-- 인덱스 확인
SHOW INDEX FROM student_video_progress;

-- UNIQUE 제약 확인
SELECT * FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'student_video_progress'
AND CONSTRAINT_TYPE = 'UNIQUE';
```

---

## Flyway 적용 방법

### 옵션 1: 애플리케이션 재시작으로 자동 적용 (권장)

Flyway가 설정되어 있다면 애플리케이션 재시작 시 자동으로 마이그레이션이 실행됩니다.

```bash
# 1. 새 버전 배포
# 2. 애플리케이션 시작 시 Flyway가 자동으로 V6, V7 실행
# 3. 로그에서 마이그레이션 성공 확인
```

**확인:**
```sql
-- Flyway 마이그레이션 히스토리 확인
SELECT * FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

-- V6, V7이 success 상태인지 확인
SELECT version, description, type, script, installed_on, success
FROM flyway_schema_history
WHERE version IN ('6', '7');
```

---

### 옵션 2: 수동 실행 (Flyway 없이)

Flyway를 사용하지 않거나 수동으로 적용해야 하는 경우:

```sql
-- 1. V6 마이그레이션 실행
SOURCE /path/to/V6__create_lesson_videos_table.sql;

-- 2. V7 마이그레이션 실행
SOURCE /path/to/V7__create_student_video_progress_table.sql;

-- 3. Flyway 히스토리 수동 기록 (Flyway 사용 시)
INSERT INTO flyway_schema_history
(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES
(6, '6', 'create lesson videos table', 'SQL', 'V6__create_lesson_videos_table.sql', NULL, USER(), 0, 1),
(7, '7', 'create student video progress table', 'SQL', 'V7__create_student_video_progress_table.sql', NULL, USER(), 0, 1);
```

---

## 테이블 의존성

```
lessons (기존)
  ↓
lesson_videos (V6) ← 수업 영상
  ↓
student_video_progress (V7) ← 학생 시청 진행률

students (기존)
  ↓
student_video_progress (V7)
```

**중요:**
- `lesson_videos`는 `lessons` 테이블에 의존
- `student_video_progress`는 `students`와 `lesson_videos` 테이블에 의존
- 순서대로 V6 → V7 실행 필수

---

## 롤백 스크립트

만약 문제가 발생하여 롤백이 필요한 경우:

```sql
-- V7 롤백 (student_video_progress 삭제)
DROP TABLE IF EXISTS student_video_progress;

-- V6 롤백 (lesson_videos 삭제)
-- ⚠️ 주의: student_video_progress가 먼저 삭제되어야 함 (외래 키 제약)
DROP TABLE IF EXISTS lesson_videos;

-- Flyway 히스토리에서 제거 (Flyway 사용 시)
DELETE FROM flyway_schema_history WHERE version IN ('6', '7');
```

**⚠️ 경고:**
- 롤백 시 해당 테이블의 모든 데이터가 삭제됩니다
- 프로덕션에서 롤백 전 반드시 데이터 백업 확인

---

## 예상 영향

### 다운타임
- **필요 없음**: DDL은 새 테이블 생성만 수행
- 기존 테이블 변경 없음
- 애플리케이션 재시작 시간만 소요

### 성능 영향
- **최소**: 빈 테이블 생성은 1초 미만 소요
- 인덱스 생성도 데이터가 없으므로 즉시 완료

### 스토리지
- **lesson_videos**: 영상 당 ~1KB 예상
  - 100개 영상 = ~100KB
- **student_video_progress**: 학생-영상 조합 당 ~200 bytes
  - 50명 학생 × 100개 영상 = ~1MB

---

## 배포 후 검증

### 1. 테이블 생성 확인
```sql
-- 테이블 목록 확인
SHOW TABLES LIKE '%video%';

-- 예상 결과:
-- lesson_videos
-- student_video_progress
```

### 2. 데이터 구조 확인
```sql
-- lesson_videos 구조
DESC lesson_videos;

-- student_video_progress 구조
DESC student_video_progress;
```

### 3. 외래 키 확인
```sql
-- lesson_videos의 외래 키
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_NAME = 'lesson_videos'
AND REFERENCED_TABLE_NAME IS NOT NULL;

-- student_video_progress의 외래 키
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_NAME = 'student_video_progress'
AND REFERENCED_TABLE_NAME IS NOT NULL;
```

### 4. 애플리케이션 기능 테스트
- [ ] 선생님: 수업에 영상 추가 가능
- [ ] 선생님: 영상 목록 조회 가능
- [ ] 학생: 영상 다시보기 화면 접근 가능
- [ ] 학생: 영상 재생 시 진행률 업데이트 확인
- [ ] 학생: 진행률 바 표시 확인

---

## 문제 해결

### 외래 키 제약 위반
```
ERROR 1215 (HY000): Cannot add foreign key constraint
```

**원인:** 참조 테이블(`lessons`, `students`)이 존재하지 않음

**해결:**
```sql
-- 참조 테이블 존재 확인
SHOW TABLES LIKE 'lessons';
SHOW TABLES LIKE 'students';
```

### 테이블 이미 존재
```
ERROR 1050 (42S01): Table 'lesson_videos' already exists
```

**원인:** 이미 테이블이 생성되어 있음

**해결:**
```sql
-- 기존 테이블 확인
SELECT * FROM lesson_videos LIMIT 1;

-- Flyway 히스토리 확인
SELECT * FROM flyway_schema_history WHERE version = '6';

-- 이미 적용되었다면 다음 마이그레이션으로 진행
```

### 권한 부족
```
ERROR 1142 (42000): CREATE command denied to user
```

**해결:**
```sql
-- 필요한 권한 확인
SHOW GRANTS FOR CURRENT_USER();

-- 관리자에게 CREATE, ALTER 권한 요청
```

---

## 추가 권장 사항

### 1. 백업 확인
```bash
# 배포 전 전체 데이터베이스 백업
mysqldump -u [user] -p suhui_secretary > backup_$(date +%Y%m%d_%H%M%S).sql

# 백업 파일 확인
ls -lh backup_*.sql
```

### 2. 테스트 환경에서 먼저 실행
```bash
# 로컬 또는 스테이징 환경에서 테스트
mysql -u [user] -p [test_database] < V6__create_lesson_videos_table.sql
mysql -u [user] -p [test_database] < V7__create_student_video_progress_table.sql
```

### 3. 모니터링
배포 후 다음 항목 모니터링:
- 애플리케이션 로그 (Flyway 마이그레이션 성공 여부)
- 데이터베이스 에러 로그
- API 응답 시간 (영상 관련 엔드포인트)
- 디스크 사용량

---

## 연락처

문제 발생 시:
1. 애플리케이션 로그 확인
2. 데이터베이스 에러 로그 확인
3. 롤백 스크립트 준비
4. 필요 시 백업에서 복구
