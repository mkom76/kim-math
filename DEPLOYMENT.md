# Kim-Math 배포 가이드

## 아키텍처 개요

**서버 구성:** 단일 서버 (DigitalOcean Droplet)
- Frontend: Vue 3 + Vite (Docker/Nginx)
- Backend: Spring Boot (Docker)
- Database: MySQL 8.0 (Docker)
- Reverse Proxy: Nginx (Docker)

**백업 시스템:** DigitalOcean Spaces (S3 호환)
- 로컬: 30일간 보관
- S3: 90일간 보관
- 자동 백업: 매일 새벽 2시

**예상 비용:** $29/month
- Droplet (2 vCPU, 4GB RAM): $24/month
- Spaces (250GB 포함): $5/month

---

## Phase 1: DigitalOcean 계정 및 인프라 설정

### 1.1 Droplet 생성

1. DigitalOcean 가입: https://www.digitalocean.com
2. "Create" → "Droplets" 클릭
3. 설정:
   - **OS**: Ubuntu 22.04 LTS
   - **Plan**: Basic
   - **CPU**: 2 vCPU, 4GB RAM, 80GB SSD ($24/month)
   - **Region**: Singapore (sgp1) - 한국과 가장 가까움
   - **Authentication**: SSH Key 추가
     ```bash
     # 로컬에서 SSH 키 생성 (없는 경우)
     ssh-keygen -t ed25519 -C "your-email@example.com"
     # 공개키 복사
     cat ~/.ssh/id_ed25519.pub
     ```
   - **Hostname**: kim-math-prod
4. "Create Droplet" 클릭
5. **IP 주소 기록** (예: 159.89.XXX.XXX)

### 1.2 DigitalOcean Spaces 생성 (S3 백업용)

1. "Create" → "Spaces Object Storage" 클릭
2. 설정:
   - **Datacenter**: Singapore (sgp1)
   - **Enable CDN**: No (백업용이므로 불필요)
   - **Space Name**: kim-math-backups
   - **Select Project**: Default
3. "Create a Space" 클릭
4. "Settings" → "API" → "Generate New Key"
   - **Name**: kim-math-backup-key
   - **Access Key와 Secret Key 기록** (다시 볼 수 없음!)

---

## Phase 2: 서버 초기 설정

### 2.1 서버 접속

```bash
ssh root@159.89.XXX.XXX
```

### 2.2 사용자 생성 및 권한 설정

```bash
# suhui 사용자 생성
adduser suhui
usermod -aG sudo suhui

# SSH 키 복사
mkdir -p /home/suhui/.ssh
cp /root/.ssh/authorized_keys /home/suhui/.ssh/
chown -R suhui:suhui /home/suhui/.ssh
chmod 700 /home/suhui/.ssh
chmod 600 /home/suhui/.ssh/authorized_keys

# 재접속
exit
ssh suhui@159.89.XXX.XXX
```

### 2.3 Docker 및 Docker Compose 설치

```bash
# 패키지 업데이트
sudo apt update
sudo apt upgrade -y

# 필수 패키지 설치
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Docker GPG 키 추가
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Docker 저장소 추가
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 설치
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io

# 사용자를 docker 그룹에 추가
sudo usermod -aG docker suhui
newgrp docker

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 설치 확인
docker --version
docker-compose --version
```

### 2.4 추가 도구 설치

```bash
# Git 설치
sudo apt install -y git

# 백업 도구 설치 (s3cmd)
sudo apt install -y s3cmd python3-dateutil

# Certbot 설치 (SSL 인증서용, 도메인이 있는 경우)
sudo apt install -y certbot
```

### 2.5 방화벽 설정

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable

# 상태 확인
sudo ufw status
```

---

## Phase 3: 애플리케이션 배포

### 3.1 코드 가져오기

```bash
# 애플리케이션 디렉토리 생성
mkdir -p /home/suhui/kim-math
cd /home/suhui/kim-math

# Git 저장소 클론
git clone https://github.com/YOUR_USERNAME/kim-math.git .
```

### 3.2 환경 변수 설정

```bash
# .env 파일 생성
nano .env
```

다음 내용을 입력 (실제 값으로 변경):

```env
# MySQL Database Configuration
MYSQL_ROOT_PASSWORD=your-super-strong-root-password-here
MYSQL_DATABASE=kim_math
MYSQL_USER=kim_math_app
MYSQL_PASSWORD=your-strong-app-password-here

# CORS Configuration
ALLOWED_ORIGINS=http://YOUR_DROPLET_IP,http://yourdomain.com,https://yourdomain.com

# S3 Backup Configuration (DigitalOcean Spaces)
S3_ENDPOINT=https://sgp1.digitaloceanspaces.com
S3_BUCKET=kim-math-backups
S3_ACCESS_KEY=YOUR_SPACES_ACCESS_KEY
S3_SECRET_KEY=YOUR_SPACES_SECRET_KEY
S3_REGION=sgp1
```

저장: `Ctrl+O`, `Enter`, `Ctrl+X`

### 3.3 스크립트 실행 권한 부여

```bash
chmod +x scripts/*.sh
```

### 3.4 Docker 컨테이너 빌드 및 실행

```bash
# 백그라운드에서 빌드 및 실행
docker-compose -f docker-compose.prod.yml up -d --build

# 로그 확인 (Ctrl+C로 종료)
docker-compose -f docker-compose.prod.yml logs -f

# 컨테이너 상태 확인
docker ps
```

**예상 빌드 시간:** 5-10분

### 3.5 데이터베이스 스키마 생성

MySQL 컨테이너가 실행되면 JPA가 자동으로 스키마를 생성합니다.
`application-prod.yml`의 `ddl-auto`가 `validate`로 설정되어 있으므로, 최초 실행 시 오류가 발생할 수 있습니다.

**해결 방법 1: 로컬에서 스키마 생성 후 복사**
```bash
# 로컬에서 애플리케이션 실행 (ddl-auto: create로 변경)
# 생성된 CREATE TABLE 문 복사

# 서버의 MySQL 컨테이너에 접속
docker exec -it kim-math-mysql mysql -u root -p

# 스키마 생성
USE kim_math;
-- CREATE TABLE 문 실행
```

**해결 방법 2: 임시로 ddl-auto 변경**
```bash
# application-prod.yml 수정
nano src/main/resources/application-prod.yml

# ddl-auto: validate → ddl-auto: update 로 변경
# 재빌드
docker-compose -f docker-compose.prod.yml up -d --build

# 스키마 생성 후 다시 validate로 변경 권장
```

### 3.6 배포 확인

```bash
# 헬스 체크
curl http://localhost/api/actuator/health

# 브라우저에서 확인
# http://YOUR_DROPLET_IP
```

로그인 테스트:
- 선생님: username=suhui, pin=123456
- 학생: studentId=1, pin=1111

---

## Phase 4: 백업 시스템 설정

### 4.1 백업 스크립트 테스트

```bash
# 백업 디렉토리 생성
mkdir -p /home/suhui/backups

# 수동 백업 테스트
cd /home/suhui/kim-math
./scripts/backup-mysql-to-s3.sh
```

성공하면 다음과 같이 표시됩니다:
```
✓ Database dump created: kim_math_backup_20250126_140530.sql.gz (2.3M)
✓ Backup uploaded to S3: s3://kim-math-backups/mysql-backups/...
✓ Backup Completed Successfully!
```

### 4.2 자동 백업 설정 (Cron)

```bash
# Crontab 편집
crontab -e

# 다음 라인 추가 (매일 새벽 2시에 백업)
0 2 * * * /home/suhui/kim-math/scripts/backup-mysql-to-s3.sh >> /home/suhui/backups/backup.log 2>&1
```

### 4.3 복구 테스트

```bash
# S3에 있는 백업 목록 확인
./scripts/restore-mysql-from-s3.sh

# 특정 백업 복구 (테스트용)
./scripts/restore-mysql-from-s3.sh kim_math_backup_20250126_140530.sql.gz
```

---

## Phase 5: SSL 인증서 설정 (도메인이 있는 경우)

### 5.1 도메인 DNS 설정

도메인 등록 업체(GoDaddy, Namecheap 등)에서:
- A 레코드 추가: `@` → `YOUR_DROPLET_IP`
- A 레코드 추가: `www` → `YOUR_DROPLET_IP`
- TTL: 3600

DNS 전파 확인 (5-60분 소요):
```bash
nslookup yourdomain.com
```

### 5.2 Let's Encrypt SSL 인증서 발급

```bash
# Nginx 컨테이너 임시 중지
docker-compose -f docker-compose.prod.yml stop nginx

# Certbot 실행
sudo certbot certonly --standalone \
  -d yourdomain.com \
  -d www.yourdomain.com \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email

# 인증서를 프로젝트 디렉토리로 복사
sudo mkdir -p /home/suhui/kim-math/certbot/conf
sudo cp -r /etc/letsencrypt/* /home/suhui/kim-math/certbot/conf/
sudo chown -R suhui:suhui /home/suhui/kim-math/certbot

# Nginx 설정 수정
nano nginx/conf.d/app.conf
# HTTPS server 블록의 주석 해제 (server { listen 443 ssl... })
# YOUR_DOMAIN을 실제 도메인으로 변경

# Nginx 재시작
docker-compose -f docker-compose.prod.yml start nginx
```

### 5.3 자동 갱신 설정

```bash
# 갱신 스크립트 생성
mkdir -p /home/suhui/scripts
nano /home/suhui/scripts/renew-ssl.sh
```

내용:
```bash
#!/bin/bash
cd /home/suhui/kim-math
docker-compose -f docker-compose.prod.yml stop nginx
certbot renew --quiet
cp -r /etc/letsencrypt/* /home/suhui/kim-math/certbot/conf/
docker-compose -f docker-compose.prod.yml start nginx
```

```bash
# 실행 권한 부여
chmod +x /home/suhui/scripts/renew-ssl.sh

# Crontab 추가 (매주 월요일 새벽 3시)
crontab -e
# 추가:
0 3 * * 1 /home/suhui/scripts/renew-ssl.sh >> /home/suhui/ssl-renewal.log 2>&1
```

---

## Phase 6: 모니터링 및 유지보수

### 6.1 로그 확인

```bash
# 전체 로그
docker-compose -f docker-compose.prod.yml logs -f

# 특정 컨테이너 로그
docker logs kim-math-backend -f
docker logs kim-math-mysql -f
docker logs kim-math-nginx -f
```

### 6.2 컨테이너 관리

```bash
# 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart

# 특정 컨테이너만 재시작
docker-compose -f docker-compose.prod.yml restart backend

# 컨테이너 중지
docker-compose -f docker-compose.prod.yml down

# 컨테이너 시작
docker-compose -f docker-compose.prod.yml up -d

# 컨테이너 상태 확인
docker ps
docker stats
```

### 6.3 업데이트 배포

```bash
cd /home/suhui/kim-math

# 최신 코드 가져오기
git pull origin main

# 재빌드 및 재시작
docker-compose -f docker-compose.prod.yml up -d --build

# 로그 확인
docker-compose -f docker-compose.prod.yml logs -f backend
```

### 6.4 데이터베이스 백업 확인

```bash
# 로컬 백업 확인
ls -lh /home/suhui/backups/

# S3 백업 확인
source /home/suhui/kim-math/.env
s3cmd ls s3://${S3_BUCKET}/mysql-backups/ \
  --access_key=${S3_ACCESS_KEY} \
  --secret_key=${S3_SECRET_KEY} \
  --host=${S3_ENDPOINT#https://} \
  --region=${S3_REGION}
```

---

## 트러블슈팅

### MySQL 컨테이너가 시작되지 않음

```bash
# 로그 확인
docker logs kim-math-mysql

# 볼륨 삭제 후 재시작 (데이터 삭제됨!)
docker-compose -f docker-compose.prod.yml down -v
docker-compose -f docker-compose.prod.yml up -d
```

### Backend가 MySQL에 연결되지 않음

```bash
# MySQL 컨테이너가 healthy 상태인지 확인
docker ps

# Backend 로그 확인
docker logs kim-math-backend

# .env 파일의 비밀번호 확인
cat .env
```

### S3 백업 실패

```bash
# S3 연결 테스트
source /home/suhui/kim-math/.env
s3cmd ls \
  --access_key=${S3_ACCESS_KEY} \
  --secret_key=${S3_SECRET_KEY} \
  --host=${S3_ENDPOINT#https://} \
  --region=${S3_REGION}

# Access Key와 Secret Key 확인
```

### 디스크 공간 부족

```bash
# 디스크 사용량 확인
df -h

# Docker 정리
docker system prune -a

# 오래된 백업 삭제
find /home/suhui/backups -name "*.sql.gz" -mtime +30 -delete
```

---

## 보안 체크리스트

- [ ] SSH 비밀번호 로그인 비활성화 (키 기반 인증만 허용)
- [ ] 방화벽(UFW) 활성화 및 필요한 포트만 개방
- [ ] MySQL root 비밀번호 강력하게 설정
- [ ] .env 파일 권한 제한 (chmod 600)
- [ ] SSL 인증서 설치 (도메인이 있는 경우)
- [ ] 자동 백업 작동 확인
- [ ] S3 Access Key 안전하게 보관
- [ ] 정기적인 시스템 업데이트 (`sudo apt update && sudo apt upgrade`)

---

## 비용 요약

| 항목 | 사양 | 월 비용 |
|------|------|---------|
| Droplet | 2 vCPU, 4GB RAM, 80GB SSD | $24 |
| Spaces | 250GB 저장소 + CDN | $5 |
| **총계** | | **$29/month** |

**연간 비용:** $348

**절약액:** Managed Database 제외로 $15/month ($180/year) 절약

---

## 지원 리소스

- **DigitalOcean 문서**: https://docs.digitalocean.com
- **Docker 문서**: https://docs.docker.com
- **MySQL 8.0 문서**: https://dev.mysql.com/doc/refman/8.0/en/
- **Let's Encrypt**: https://letsencrypt.org/docs/

---

**배포 완료 후 확인사항:**
1. 브라우저에서 앱 접속 가능
2. 로그인 기능 작동
3. 데이터베이스 CRUD 작동
4. 백업 스크립트 실행 확인
5. S3에 백업 파일 업로드 확인
