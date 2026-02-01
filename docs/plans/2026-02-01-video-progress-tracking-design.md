# 학생 영상 시청 진행률 추적 기능 설계

## 목표

학생들이 수업 영상을 얼마나 시청했는지 추적하여 선생님이 학습 현황을 파악할 수 있도록 함

## Architecture

### 접근 방식: YouTube iframe API + 주기적 업데이트

- YouTube iframe API의 `getCurrentTime()`, `getDuration()` 사용
- 30초마다 백엔드에 진행률 전송
- 건너뛰기 감지 및 차단
- 진행률 후퇴 방지

### 서버 부하 분석

- 최대 50명 동시 시청 가정
- 30초마다 업데이트 = 100 requests/min
- 단순 UPDATE 쿼리로 부하 최소

## Data Model

### 새 테이블: student_video_progress

```sql
CREATE TABLE student_video_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_time INT NOT NULL DEFAULT 0,        -- 현재 재생 위치 (초)
    duration INT NOT NULL DEFAULT 0,             -- 전체 영상 길이 (초)
    completed BOOLEAN NOT NULL DEFAULT FALSE,    -- 완료 여부 (90% 이상)
    last_watched_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (lesson_video_id) REFERENCES lesson_videos(id) ON DELETE CASCADE,

    UNIQUE KEY uk_student_video (student_id, lesson_video_id),
    INDEX idx_student_id (student_id),
    INDEX idx_lesson_video_id (lesson_video_id),
    INDEX idx_completed (completed)
);
```

**진행률 계산:** `(current_time / duration) × 100`

**완료 기준:** 90% 이상 시청 시 `completed = true`
- 이유: 엔딩 크레딧 건너뛰어도 완료로 간주

## API Design

### 1. 진행률 업데이트 (학생용)

```
PUT /api/students/{studentId}/videos/{videoId}/progress

Request:
{
  "currentTime": 145,
  "duration": 600
}

Response:
{
  "currentTime": 145,
  "duration": 600,
  "progressPercent": 24,
  "completed": false
}
```

### 2. 학생의 모든 영상 진행률 조회 (학생용)

```
GET /api/students/{studentId}/videos/progress

Response:
[
  {
    "videoId": 1,
    "currentTime": 145,
    "duration": 600,
    "progressPercent": 24,
    "completed": false,
    "lastWatchedAt": "2026-02-01T14:30:00"
  }
]
```

### 3. 수업별 학생 시청 현황 (선생님용)

```
GET /api/lessons/{lessonId}/videos/stats

Response:
{
  "videos": [
    {
      "videoId": 1,
      "title": "수학 1부",
      "studentProgress": [
        {
          "studentId": 10,
          "studentName": "김철수",
          "progressPercent": 100,
          "completed": true,
          "lastWatchedAt": "2026-02-01T14:30:00"
        }
      ]
    }
  ]
}
```

## Frontend Implementation

### 학생 화면 (StudentVideosView.vue)

**YouTube iframe API 통합:**
```typescript
// 건너뛰기 감지 로직
const INTERVAL_SEC = 30
const MAX_PLAYBACK_SPEED = 2.0  // YouTube 최대 배속
const TOLERANCE = 5
const MAX_ALLOWED = INTERVAL_SEC * MAX_PLAYBACK_SPEED + TOLERANCE  // 65초

if (timeDiff > MAX_ALLOWED) {
  // 건너뛰기 감지 - 업데이트 안 함
  return
}
```

**진행률 표시:**
- 영상 카드 하단에 `<el-progress>` 컴포넌트
- 완료: 초록색 + 체크마크
- 진행중: 파란색 + 퍼센트
- 미시청: 회색 + "미시청" 표시

### 선생님 화면 (LessonDetailView.vue)

**시청 현황 다이얼로그:**
- 영상별 "시청현황" 버튼 추가
- 학생별 진행률 테이블 표시
- 필터: 전체/완료/진행중/미시청

## Error Handling

### 네트워크 실패
- 조용히 실패 (사용자 경험 방해 안 함)
- 다음 30초 간격에서 재시도

### 건너뛰기 차단
- 65초(30초×2배속+5초) 이상 점프 감지
- 프론트엔드에서 업데이트 전송 안 함

### 진행률 후퇴 방지
- 백엔드에서 `currentTime > 기존값`만 저장
- 뒤로 돌려서 다시 봐도 진행률 유지
- 단, `completed = true`는 한번 설정되면 유지

### 배속 재생
- 2배속까지 정상 작동
- YouTube API는 실제 타임라인 위치 반환

## UI/UX

### 학생 화면
- 기존 영상 카드 레이아웃 유지
- 카드 하단에 진행률 바 추가
- 완료/진행중/미시청 상태 표시

### 선생님 화면
- 영상별 시청 현황 버튼
- 학생별 진행률 테이블
- 정렬: 진행률 오름차순 (안 본 학생 먼저)

## Testing Strategy

### 기능 테스트
- 1배속/2배속 시청
- 건너뛰기 차단
- 진행률 후퇴 방지
- 완료 처리 (90% 기준)

### 성능 테스트
- 50명 동시 시청 시뮬레이션
- DB 쿼리 성능 확인

### 엣지 케이스
- 네트워크 끊김
- 페이지 새로고침
- 여러 탭에서 동시 재생

## Implementation Order

1. Backend: Entity, Repository, DTO
2. Backend: Service layer (진행률 업데이트 로직)
3. Backend: Controllers
4. Frontend: API client
5. Frontend: 학생 화면 (진행률 업데이트 + 표시)
6. Frontend: 선생님 화면 (시청 현황)
7. Testing
