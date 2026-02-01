# 영상 시청 진행률 추적 기능

## 개요

학생들의 수업 영상 시청 진행률을 자동으로 추적하여 선생님이 학습 현황을 파악할 수 있도록 하는 기능입니다.

## 주요 기능

### 학생 화면

1. **자동 진행률 추적**
   - 영상 재생 중 30초마다 자동으로 진행률 업데이트
   - 1배속, 2배속 모두 정확하게 추적
   - 네트워크 오류 시 조용히 실패 (사용자 경험 방해하지 않음)

2. **진행률 표시**
   - 각 영상 카드 하단에 진행률 바 표시
   - 완료 상태: 초록색 진행률 바 + 체크마크 + "시청 완료"
   - 진행 중: 파란색 진행률 바 + "N% 시청중"
   - 미시청: 회색 진행률 바 + "미시청"

3. **마지막 시청 시간**
   - "5분 전", "2시간 전", "3일 전" 형식으로 표시

4. **건너뛰기 방지**
   - 65초 이상 앞으로 건너뛰면 진행률 업데이트 안 함
   - 정상 시청만 진행률로 인정
   - 2배속 재생 고려 (30초 × 2.0배속 + 5초 여유 = 65초)

5. **진행률 후퇴 방지**
   - 한번 본 부분을 다시 봐도 진행률이 뒤로 가지 않음
   - 최대 시청 위치를 기준으로 진행률 유지

### 완료 기준

- 영상의 **90% 이상** 시청 시 자동으로 완료 처리
- 엔딩 크레딧 등을 건너뛰어도 완료로 인정

## 기술 세부사항

### 백엔드

**데이터베이스 스키마:**
```sql
CREATE TABLE student_video_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_time INT NOT NULL DEFAULT 0,        -- 현재 재생 위치 (초)
    duration INT NOT NULL DEFAULT 0,            -- 전체 영상 길이 (초)
    completed BOOLEAN NOT NULL DEFAULT FALSE,   -- 완료 여부
    last_watched_at TIMESTAMP,                  -- 마지막 시청 시간
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_student_video (student_id, lesson_video_id)
);
```

**API 엔드포인트:**

1. 진행률 업데이트 (학생용)
   ```
   PUT /api/students/{studentId}/videos/{videoId}/progress

   Request Body:
   {
     "currentTime": 145,
     "duration": 600
   }

   Response:
   {
     "videoId": 1,
     "currentTime": 145,
     "duration": 600,
     "progressPercent": 24,
     "completed": false,
     "lastWatchedAt": "2026-02-01T14:30:00"
   }
   ```

2. 학생 진행률 조회 (학생용)
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

**진행률 업데이트 로직:**
- `currentTime > 기존 currentTime`인 경우만 업데이트 (후퇴 방지)
- 진행률 90% 이상 시 자동으로 `completed = true` 설정
- `last_watched_at` 자동 갱신

### 프론트엔드

**YouTube iframe API 통합:**
- 동적으로 YouTube iframe API 스크립트 로드
- `getCurrentTime()`, `getDuration()` 사용하여 재생 위치 추적

**진행률 업데이트 주기:**
- 30초마다 백엔드에 진행률 전송
- 영상 재생 시작 5초 후 최초 업데이트

**건너뛰기 감지 알고리즘:**
```typescript
const INTERVAL_SEC = 30
const MAX_ALLOWED = 65  // 30 × 2.0배속 + 5초 여유

const timeDiff = currentTime - lastCurrentTime
if (timeDiff > MAX_ALLOWED) {
  // 건너뛰기 감지 - 업데이트 안 함
  return
}
```

**리소스 정리:**
- 다이얼로그 닫을 때 자동으로 업데이트 인터벌 정리
- YouTube player 인스턴스 제거

## 사용 시나리오

### 시나리오 1: 정상 시청
1. 학생이 영상 재생
2. 2분 동안 1배속으로 시청
3. 4회 진행률 업데이트 (30초, 1분, 1분30초, 2분)
4. 진행률 바에 진행 상황 표시

### 시나리오 2: 배속 시청
1. 학생이 2배속으로 재생
2. 실제 1분 시청 = 영상 2분 분량
3. 2회 진행률 업데이트 (30초, 1분)
4. 정상적으로 진행률 반영

### 시나리오 3: 건너뛰기 시도
1. 학생이 1분까지 시청
2. 5분 위치로 건너뛰기 (4분 점프)
3. 건너뛰기 감지 (240초 > 65초)
4. 진행률 업데이트 안 됨, 1분에서 유지
5. 콘솔에 "Skip detected, not updating progress" 로그

### 시나리오 4: 완료 처리
1. 학생이 6분 영상을 5분 24초까지 시청 (90%)
2. 자동으로 완료 처리
3. 초록색 진행률 바 + 체크마크 표시

### 시나리오 5: 이어보기
1. 학생이 50%까지 시청 후 브라우저 종료
2. 다음날 다시 접속
3. 진행률 바에 50% 표시
4. "1일 전" 타임스탬프 표시

## 알려진 제한사항

1. **진행률 손실 가능성**
   - 30초 간격으로 업데이트하므로 최대 30초까지 진행률 손실 가능
   - 브라우저 크래시, 강제 종료 시 마지막 업데이트 이후 진행률 손실

2. **건너뛰기 허용 범위**
   - 65초까지 점프는 허용 (2배속 지원)
   - 3배속 이상은 YouTube가 공식 지원하지 않으므로 고려 안 함

3. **멀티 디바이스**
   - 여러 기기에서 동시 시청 시 마지막 업데이트가 우선
   - 잠재적 경합 상태 존재하나 실용적으로는 문제 없음

4. **비활성 탭**
   - 브라우저 탭이 비활성 상태일 때 업데이트 간격이 불규칙할 수 있음
   - 브라우저의 백그라운드 타이머 throttling 때문

## 서버 부하

**예상 부하 (최대 50명 동시 시청 가정):**
- 30초마다 업데이트 = 100 requests/min
- 단순 UPDATE 쿼리로 부하 최소
- 인덱스 최적화로 쿼리 성능 확보

**데이터베이스 인덱스:**
- `idx_student_id`: 학생별 진행률 조회
- `idx_lesson_video_id`: 영상별 진행률 조회
- `idx_completed`: 완료 여부 필터링
- `uk_student_video`: 중복 방지 (UNIQUE 제약)

## 문제 해결

### 진행률이 업데이트되지 않을 때

1. 브라우저 콘솔 확인
   - YouTube API 로드 확인 (`window.YT` 존재 여부)
   - 에러 메시지 확인

2. 네트워크 탭 확인
   - `PUT /students/{id}/videos/{id}/progress` 요청 확인
   - 응답 상태 코드 확인 (200 OK 예상)

3. 로그인 상태 확인
   - 학생으로 로그인되어 있는지 확인

### 진행률 바가 표시되지 않을 때

1. Vue DevTools로 `progressMap` 확인
   - 데이터가 제대로 로드되었는지 확인

2. API 응답 확인
   - `videoId`가 영상 카드의 `video.id`와 일치하는지 확인

3. 콘솔 에러 확인
   - 진행률 조회 API 호출 실패 여부 확인

## 향후 개선 사항

1. **선생님 시청 현황 대시보드** (백엔드 DTO는 준비됨)
   - 수업별 학생 시청 현황
   - 완료/미완료 필터링
   - 진행률 정렬

2. **자동 테스트 추가**
   - 백엔드: 롤백 방지, 완료 처리 로직 단위 테스트
   - 프론트엔드: 건너뛰기 감지 로직 테스트

3. **성능 최적화**
   - 진행률 업데이트 배치 처리
   - 캐싱 전략 추가

4. **기능 개선**
   - 영상 중간부터 이어보기 (저장된 위치에서 재생 시작)
   - 시청 속도 추적 (1배속/2배속 통계)
   - 클리닉 영상 진행률 추적
