# GitHub Actions CI/CD 설정 가이드

이 가이드는 GitHub Actions를 사용하여 자동 빌드 및 배포를 설정하는 방법을 설명합니다.

## 왜 GitHub Actions를 사용하나요?

- **메모리 절약**: 서버에서 빌드하지 않고 GitHub 서버에서 빌드
- **빌드 속도**: GitHub의 강력한 서버에서 빌드 (무료 2000분/월)
- **서버 안정성**: 1GB RAM Droplet에서도 안정적으로 실행 가능

## 1단계: GitHub Secrets 설정

GitHub 저장소 페이지에서:

1. **Settings** → **Secrets and variables** → **Actions** → **New repository secret** 클릭

2. 다음 3개의 Secret을 추가:

### SERVER_HOST
```
값: kim-math.site (또는 서버 IP 주소)
```

### SERVER_USER
```
값: ubuntu (또는 서버 사용자 이름)
```

### SSH_PRIVATE_KEY
```
값: SSH 개인 키 전체 내용
```

#### SSH 개인 키 가져오기:

**로컬 컴퓨터에서** (서버가 아님):
```bash
# macOS/Linux
cat ~/.ssh/id_rsa

# 또는 서버 접속에 사용하는 키 파일
cat ~/.ssh/your-key-name
```

출력된 내용 전체를 복사 (-----BEGIN부터 -----END까지):
```
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
...
(전체 내용)
...
-----END OPENSSH PRIVATE KEY-----
```

## 2단계: 서버에 .env 파일 업데이트

서버에 SSH 접속 후:

```bash
cd /home/suhui/kim-math

# .env 파일 편집
nano .env
```

다음 내용 추가:
```bash
GITHUB_REPOSITORY_OWNER=mkom76
```

저장: `Ctrl+O`, `Enter`, `Ctrl+X`

## 3단계: 코드 푸시하고 배포 시작

로컬에서:

```bash
git add .
git commit -m "Setup GitHub Actions CI/CD"
git push origin main
```

푸시하면 자동으로:
1. GitHub Actions가 빌드 시작
2. Docker 이미지를 GitHub Container Registry에 푸시
3. 서버에 SSH 접속하여 이미지 pull 및 재시작

## 4단계: 배포 상태 확인

GitHub 저장소 페이지에서:
- **Actions** 탭 클릭
- 실행 중인 워크플로우 확인
- 각 단계별 로그 확인 가능

## 배포 과정

```
1. 코드 푸시 (main 브랜치)
   ↓
2. GitHub Actions 시작
   ↓
3. Docker 이미지 빌드 (Backend + Frontend)
   ↓
4. GitHub Container Registry에 푸시
   ↓
5. 서버 SSH 접속
   ↓
6. 이미지 pull & 컨테이너 재시작
   ↓
7. 배포 완료! 🎉
```

## 장점

- ✅ 서버 메모리 사용량 최소화
- ✅ 빌드 실패 시 서버 영향 없음
- ✅ 빌드 로그 GitHub에서 확인 가능
- ✅ 롤백 쉬움 (이전 이미지 태그 사용)
- ✅ 자동화된 배포

## 문제 해결

### 배포가 실패하면?

1. **Actions 탭에서 로그 확인**
2. **빌드 단계 실패**: Dockerfile 문제 확인
3. **배포 단계 실패**: SSH 접속 정보 확인

### 수동으로 배포하려면?

서버에서:
```bash
cd /home/suhui/kim-math
git pull origin main
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

### 이미지가 안 받아져요

서버에서 GitHub Container Registry 로그인:
```bash
# Personal Access Token 생성 필요 (repo, read:packages 권한)
echo YOUR_GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

## 비용

- **GitHub Actions**: 무료 2000분/월 (충분함)
- **GitHub Container Registry**: Public 저장소는 무료
- **서버 비용**: 1GB RAM Droplet으로 충분 ($6/월)

총 비용: **$6/월** (서버만)
