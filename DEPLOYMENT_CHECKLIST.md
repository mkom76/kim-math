# 배포 체크리스트

## 1. 로컬 환경 준비 및 커밋

### 1.1 변경사항 확인
```bash
git status
```

### 1.2 Git 커밋 및 푸시
```bash
# 새로운 파일 추가
git add .

# 커밋 메시지 작성
git commit -m "프로덕션 배포 준비: Docker 설정, API 수정, 더미 데이터 추가"

# GitHub에 푸시
git push origin main
```

---

## 2. 서버 환경 준비 (DigitalOcean Droplet)

### 2.1 서버 접속
```bash
ssh root@YOUR_SERVER_IP
```

### 2.2 필수 패키지 설치
```bash
# 시스템 업데이트
apt update && apt upgrade -y

# Docker 설치
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Docker Compose 설치
apt install docker-compose-plugin -y

# Git 설치
apt install git -y
```

### 2.3 방화벽 설정
```bash
# UFW 방화벽 활성화
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

---

## 3. 애플리케이션 배포

### 3.1 코드 클론
```bash
cd /root
git clone https://github.com/YOUR_USERNAME/kim-math.git
cd kim-math
```

### 3.2 환경 변수 설정
```bash
# .env 파일 생성
nano .env
```

**중요: 프로덕션 환경 변수 설정**
```env
# MySQL Database Configuration
MYSQL_ROOT_PASSWORD=강력한_루트_비밀번호_여기에
MYSQL_DATABASE=kim_math
MYSQL_USER=kim_math_app
MYSQL_PASSWORD=강력한_앱_비밀번호_여기에

# CORS Configuration
ALLOWED_ORIGINS=http://yourdomain.com,https://yourdomain.com

# S3 Backup Configuration (DigitalOcean Spaces)
S3_ENDPOINT=https://sgp1.digitaloceanspaces.com
S3_BUCKET=kim-math-backups
S3_ACCESS_KEY=실제_액세스_키
S3_SECRET_KEY=실제_시크릿_키
S3_REGION=sgp1
```

### 3.3 MySQL 초기화 스크립트 확인
```bash
# mysql/init 디렉토리 확인
ls -la mysql/init/

# 필요시 초기화 스크립트 추가
```

### 3.4 Docker Compose로 빌드 및 실행
```bash
# 이미지 빌드
docker compose -f docker-compose.prod.yml build

# 컨테이너 시작
docker compose -f docker-compose.prod.yml up -d

# 로그 확인
docker compose -f docker-compose.prod.yml logs -f
```

### 3.5 컨테이너 상태 확인
```bash
# 모든 컨테이너가 healthy 상태인지 확인
docker ps

# 개별 서비스 로그 확인
docker logs kim-math-backend
docker logs kim-math-frontend
docker logs kim-math-mysql
docker logs kim-math-nginx
```

---

## 4. SSL 인증서 설정 (HTTPS)

### 4.1 도메인 DNS 설정
- 도메인의 A 레코드를 서버 IP로 설정
- 변경사항 적용 대기 (최대 48시간, 보통 몇 분~몇 시간)

### 4.2 Certbot으로 SSL 인증서 발급
```bash
# Certbot 설치
apt install certbot -y

# 인증서 발급 (Standalone 모드)
docker compose -f docker-compose.prod.yml stop nginx

certbot certonly --standalone \
  -d yourdomain.com \
  -d www.yourdomain.com \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

docker compose -f docker-compose.prod.yml start nginx
```

### 4.3 nginx 설정에서 HTTPS 활성화
```bash
nano nginx/conf.d/app.conf
```

주석 처리된 HTTPS 서버 블록의 주석을 제거하고, 도메인 이름을 실제 도메인으로 변경

```bash
# nginx 재시작
docker compose -f docker-compose.prod.yml restart nginx
```

### 4.4 SSL 인증서 자동 갱신 설정
```bash
# Cron job 추가
crontab -e

# 다음 라인 추가 (매일 새벽 3시에 인증서 갱신 시도)
0 3 * * * certbot renew --quiet --deploy-hook "docker compose -f /root/kim-math/docker-compose.prod.yml restart nginx"
```

---

## 5. 백업 설정

### 5.1 백업 스크립트 실행 권한 부여
```bash
chmod +x scripts/backup.sh
chmod +x scripts/restore.sh
```

### 5.2 자동 백업 Cron job 설정
```bash
crontab -e

# 다음 라인 추가 (매일 새벽 2시에 백업)
0 2 * * * /root/kim-math/scripts/backup.sh
```

### 5.3 백업 테스트
```bash
# 수동 백업 실행
./scripts/backup.sh

# 백업 파일 확인
ls -lh backups/
```

---

## 6. 애플리케이션 테스트

### 6.1 브라우저 테스트
1. http://YOUR_SERVER_IP 또는 https://yourdomain.com 접속
2. 선생님 로그인: `suhui` / `123456`
3. 학생 로그인: `1` / `1111`
4. 주요 기능 테스트:
   - 학생 관리
   - 시험 관리
   - 숙제 관리
   - 수업 관리
   - 일일 피드백

### 6.2 API 테스트
```bash
# 헬스 체크
curl http://localhost/api/students

# 로그인 테스트
curl -X POST http://localhost/api/auth/teacher/login \
  -H "Content-Type: application/json" \
  -d '{"username":"suhui","pin":"123456"}'
```

---

## 7. 모니터링 및 유지보수

### 7.1 로그 확인
```bash
# 실시간 로그 모니터링
docker compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker logs kim-math-backend --tail 100 -f
```

### 7.2 디스크 사용량 확인
```bash
df -h
docker system df
```

### 7.3 업데이트 배포
```bash
# 코드 업데이트
cd /root/kim-math
git pull origin main

# 재빌드 및 재시작
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d

# 로그 확인
docker compose -f docker-compose.prod.yml logs -f
```

---

## 8. 보안 체크리스트

- [ ] `.env` 파일에 강력한 비밀번호 설정
- [ ] MySQL 포트(3306)가 외부에 노출되지 않도록 확인
- [ ] SSH 키 기반 인증 사용 (비밀번호 로그인 비활성화)
- [ ] 방화벽(UFW) 활성화 및 필요한 포트만 허용
- [ ] SSL/HTTPS 설정
- [ ] 정기 백업 설정 확인
- [ ] Docker 컨테이너 자동 재시작 설정 (`restart: unless-stopped`)

---

## 9. 문제 해결

### 컨테이너가 시작되지 않을 때
```bash
# 로그 확인
docker compose -f docker-compose.prod.yml logs

# 컨테이너 상태 확인
docker ps -a

# 컨테이너 재시작
docker compose -f docker-compose.prod.yml restart
```

### 데이터베이스 연결 오류
```bash
# MySQL 컨테이너 로그 확인
docker logs kim-math-mysql

# MySQL 접속 테스트
docker exec -it kim-math-mysql mysql -u kim_math_app -p
```

### 디스크 공간 부족
```bash
# 사용하지 않는 Docker 이미지 정리
docker system prune -a

# 오래된 백업 파일 삭제
find backups/ -name "*.sql.gz" -mtime +30 -delete
```

---

## 10. 긴급 연락처 및 문서

- **GitHub Repository**: https://github.com/YOUR_USERNAME/kim-math
- **상세 배포 가이드**: DEPLOYMENT.md
- **DigitalOcean 대시보드**: https://cloud.digitalocean.com
- **Spaces 대시보드**: https://cloud.digitalocean.com/spaces

---

## 주요 명령어 요약

```bash
# 서버 접속
ssh root@YOUR_SERVER_IP

# 애플리케이션 디렉토리로 이동
cd /root/kim-math

# 컨테이너 상태 확인
docker ps

# 로그 확인
docker compose -f docker-compose.prod.yml logs -f

# 재시작
docker compose -f docker-compose.prod.yml restart

# 중지
docker compose -f docker-compose.prod.yml down

# 시작
docker compose -f docker-compose.prod.yml up -d

# 백업
./scripts/backup.sh

# 복원
./scripts/restore.sh backups/backup-YYYYMMDD-HHMMSS.sql.gz
```
