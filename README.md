# 학원 학생 성적 관리 시스템

## 프로젝트 개요
학원에서 학생들의 성적을 관리하는 웹 애플리케이션입니다.

## 기술 스택
- **Backend**: Spring Boot 3.x, SQLite, JPA
- **Frontend**: React, Vite, Tailwind CSS
- **상태관리**: React Query

## 주요 기능

### 1. 학생 관리
- 학생 목록 조회 (페이징, 검색)
- 학생 등록/수정/삭제

### 2. 시험 관리  
- 시험 목록 조회
- 시험 생성/삭제
- 시험 정답 입력 및 수정

### 3. 성적 분석
- 시험별 전체 평균 점수
- 문제별 정답률 통계
- 학생별 점수 조회

### 4. 피드백 관리
- 시험별/학생별 피드백 작성
- 피드백 조회/수정/삭제

## YouTube Integration

This application integrates with YouTube Data API v3 to fetch video metadata.

### Setup

1. Follow the [YouTube API Setup Guide](docs/YOUTUBE_API_SETUP.md)
2. Set the `YOUTUBE_API_KEY` environment variable
3. Restart the application

### Features

- Teachers can add YouTube video links to lessons
- Video title, thumbnail, and duration are automatically fetched
- Students can watch lesson videos in a dedicated view

## 실행 방법

### Backend 실행
```bash
# 프로젝트 루트에서
./gradlew bootRun
```
서버는 http://localhost:8080 에서 실행됩니다.

### Frontend 실행
```bash
# frontend 디렉토리로 이동
cd frontend

# 의존성 설치 (최초 1회)
npm install

# 개발 서버 실행
npm run dev
```
프론트엔드는 http://localhost:5173 에서 실행됩니다.

## API 엔드포인트

### 학생 관리
- `GET /api/students` - 학생 목록 조회
- `POST /api/students` - 학생 등록
- `PUT /api/students/{id}` - 학생 정보 수정
- `DELETE /api/students/{id}` - 학생 삭제

### 시험 관리
- `GET /api/tests` - 시험 목록 조회
- `POST /api/tests` - 시험 생성
- `GET /api/tests/{id}/stats` - 시험 통계 조회
- `PUT /api/tests/{id}/answers` - 시험 정답 입력

### 제출 및 피드백
- `POST /api/submissions` - 답안 제출
- `GET /api/submissions/{id}/feedback` - 피드백 조회
- `POST /api/submissions/{id}/feedback` - 피드백 작성

## 데이터베이스
SQLite 파일 기반 데이터베이스를 사용하며, 애플리케이션 실행 시 자동으로 생성됩니다.
- DB 파일 위치: `./academy.db`

## 테스트 데이터
애플리케이션 최초 실행 시 자동으로 테스트 데이터가 생성됩니다:
- 학생 5명
- 시험 2개
- 각 시험당 10문제
- 학생별 답안 및 채점 결과
- 일부 학생에 대한 피드백