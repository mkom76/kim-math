# Kim Math

학원 운영자·선생님·학생이 수업, 시험, 숙제, 피드백, 클리닉과 학습 영상을 한곳에서 관리하는 학원 학습 관리 시스템입니다. 웹 관리 화면과 학생용 Android 앱을 함께 제공합니다.

## 주요 기능

- 학원·반·학생 관리와 학생 일괄 등록, 보호자 동의
- 학원별 관리자·담당 선생님·보조 선생님 권한과 데이터 격리
- 수업별 출결, 공지, 시험·숙제 배정과 학생별 진도 관리
- 객관식·주관식·서술형 시험, 제출·채점·통계와 성적 공개 제어
- 교재와 문항 DB, 단원 분류, 시험·숙제 문항 재사용
- 학생별 일일 피드백과 OpenAI 기반 피드백 초안 생성
- 보충 클리닉 등록·출결·숙제 진행 관리
- YouTube 학습 영상 등록과 학생별 시청 진도 추적
- 학생용 Capacitor Android 앱, 생체 인증과 FCM 푸시 알림

## 기술 스택

| 영역 | 구성 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 3.5, Spring MVC/WebFlux, Spring Security, Spring Data JPA |
| 데이터베이스 | MySQL 8.0, H2(테스트) |
| 프론트엔드 | Vue 3, TypeScript, Vite 7, Pinia, Vue Router, Element Plus, Axios |
| 모바일 | Capacitor 8, Android SDK, 생체 인증, Firebase Cloud Messaging |
| 외부 연동 | OpenAI API, YouTube Data API v3, Firebase Admin SDK |
| 배포 | Docker Compose, Nginx, GitHub Actions, GHCR |
| 테스트 | JUnit 5, Spring Security Test, Vitest, Vue Test Utils |

## 저장소 구조

```text
.
├── src/main/java/com/example/   # Spring Boot API와 도메인 로직
├── src/test/                    # 백엔드 통합·단위 테스트
├── frontend/                    # Vue 웹과 Capacitor Android 앱
├── migrations/                  # 운영자가 수동 적용하는 MySQL 마이그레이션
├── backend/                     # 백엔드 Docker 이미지
├── nginx/                       # 운영 리버스 프록시와 TLS 설정
├── scripts/                     # 백업·복구·인증서·Android 보조 스크립트
├── docker-compose.prod.yml      # 운영 서비스 구성
└── docs/                        # 아키텍처와 기능별 운영 문서
```

상세한 도메인 구조, 요청 흐름, 권한 모델과 API 영역은 [프로젝트 구조 문서](docs/PROJECT_OVERVIEW.md)를 참고하세요.

## 로컬 개발

### 사전 준비

- JDK 21
- Docker와 Docker Compose 또는 로컬 MySQL 8.0
- Node.js 24 (`.nvmrc` 기준)와 npm

### 1. MySQL 실행

운영 Compose 파일에서 MySQL만 실행할 수 있습니다.

```bash
cp .env.example .env
# .env의 MYSQL_* 값을 로컬 개발용 값으로 수정
docker compose -f docker-compose.prod.yml up -d mysql
```

### 2. 백엔드 실행

로컬 프로필은 시작할 때 스키마와 샘플 데이터를 만들고 종료할 때 제거합니다. 보존할 데이터베이스에는 로컬 프로필을 사용하지 마세요.

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/kim_math?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Seoul'
export SPRING_DATASOURCE_USERNAME='kim_math_app'
export SPRING_DATASOURCE_PASSWORD='로컬_MYSQL_비밀번호'

./gradlew bootRun
```

백엔드는 `http://localhost:8080`에서 실행됩니다. OpenAI·YouTube·FCM 설정은 해당 기능을 사용할 때만 추가하면 됩니다.

### 3. 프론트엔드 실행

```bash
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://localhost:5173`을 엽니다. 개발 모드의 API 클라이언트는 현재 프론트엔드 호스트의 `8080` 포트로 요청하고, 세션 쿠키와 CSRF 토큰을 사용합니다.

### 샘플 로그인

`local` 프로필의 `DataInitializer`가 아래 계정을 포함한 샘플 데이터를 생성합니다.

| 구분 | ID | PIN | 비고 |
| --- | --- | --- | --- |
| 선생님 | `suhui` | `123456` | 두 샘플 학원의 관리자 |
| 학생 | `1` | `1111` | 첫 번째 샘플 학생 |

## 테스트와 빌드

```bash
# 백엔드 테스트
./gradlew test

# 프론트엔드 테스트
cd frontend && npm test

# 타입 검사와 프로덕션 빌드
cd frontend && npm run build:check
```

백엔드 테스트는 MySQL 호환 모드의 인메모리 H2를 사용하며 CSRF를 비활성화합니다. 실제 MySQL 마이그레이션과 운영 보안 설정은 별도로 검증해야 합니다.

## Android 개발

학생 앱은 `com.kimmath.student` Capacitor Android 앱입니다. 에뮬레이터나 USB 기기에서 로컬 백엔드에 연결할 때는 다음 순서로 실행합니다.

```bash
cd frontend
npm run android:devices
npm run android:reverse
npm run android:build
npm run android:install
```

`android:reverse`는 기기의 `localhost:8080`을 개발 머신의 백엔드로 연결합니다. 네이티브 빌드·푸시 운영 절차는 [Android 푸시 알림 가이드](docs/ANDROID_PUSH_GUIDE.md)를 참고하세요.

## 선택 환경 변수

| 변수 | 용도 | 기본 동작 |
| --- | --- | --- |
| `OPENAI_API_KEY` | AI 피드백 생성 | 키가 없으면 AI 호출 불가 |
| `YOUTUBE_API_KEY` | 영상 메타데이터 조회 | 키가 없으면 영상 신규 등록 불가 |
| `PUSH_ENABLED` | FCM 실제 발송 활성화 | `false` |
| `FCM_CREDENTIALS_PATH` | Firebase 서비스 계정 JSON 경로 | 미지정 시 ADC 사용 |
| `REMEMBER_ME_TTL_DAYS` | 자동 로그인 토큰 수명 | 30일 |

비밀값과 Firebase 서비스 계정 파일은 Git에 커밋하지 마세요. YouTube 키 발급 방법은 [YouTube API 설정 가이드](docs/YOUTUBE_API_SETUP.md)에 정리되어 있습니다.

## 배포와 데이터베이스

`main` 푸시는 GitHub Actions에서 백엔드·프론트엔드 이미지를 빌드해 GHCR에 푸시하고 운영 서버의 Docker Compose 서비스를 갱신합니다. 운영 구성은 Nginx가 `/api/`를 백엔드로, 나머지 요청을 Vue 정적 서버로 프록시합니다.

운영 JPA 설정은 `ddl-auto: validate`입니다. 이 저장소는 Flyway나 Liquibase를 사용하지 않으며, `migrations/`의 SQL을 운영자가 백업·검증·롤백 계획과 함께 직접 적용해야 합니다.

- [배포 가이드](DEPLOYMENT.md)
- [CI/CD 설정](CICD_SETUP.md)
- [배포 체크리스트](DEPLOYMENT_CHECKLIST.md)
- [마이그레이션 런북](migrations/README.md)
- [변경 기록](CHANGELOG.md)

## 보안 모델 요약

- 인증은 서버 세션과 HttpOnly 쿠키를 사용하며 변경 요청은 CSRF 토큰이 필요합니다.
- 선생님은 로그인 후 활성 학원을 선택하고, 해당 학원의 멤버십 역할로 권한을 얻습니다.
- `ACADEMY_ADMIN`은 학원 전체, `TEACHER`는 담당 반, `ASSISTANT`는 배정된 반 범위로 제한됩니다.
- Hibernate 필터가 목록 쿼리를 격리하고, 기본 키 조회와 변경 작업은 서비스 계층에서 추가 권한 검사를 수행해야 합니다.
- 학생 API는 로그인한 학생 자신의 데이터만 반환·변경하도록 서비스 계층에서 검증해야 합니다.
