# Migrations Runbook

이 디렉토리의 SQL은 운영자가 직접 실행하는 일회성 마이그레이션입니다. 자동 마이그레이션 도구(예: Flyway, Liquibase)는 사용하지 않습니다.

## 2026-11-13: PDF 숙제 등록과 문항 유형

시험 PDF 확장은 `2026-11-13-02-add-exam-pdf-imports.sql`로 `exam_pdf_imports`, `exam_pdf_questions` 두 테이블만 추가합니다. 기존 시험·문항·제출·성적은 수정하지 않습니다. 이 SQL도 DB와 비공개 PDF 볼륨을 함께 백업하고 코드 배포 전에 적용·검증해야 합니다. 기존 숙제용 일회성 마이그레이션 작업은 시험 테이블을 생성하지 않으므로 재실행하지 않습니다. 중간 실패 시 자동 재시도나 테이블 삭제 없이 적용 상태를 확인합니다.

소유자가 위 절차를 승인한 경우 `homework-pdf-exam-migrate`를 검토한 작업 브랜치에서 실행합니다. 예상 서버 SHA·운영 DB 식별값·LF 줄바꿈 SQL 해시를 고정하고 DB+PDF 공동 백업 및 격리 복원·SQL 예행 검증이 완료된 뒤에만 신규 테이블을 생성합니다. 기존 테이블이나 일부 적용 흔적이 있으면 중단합니다. 기존 환경 설정·PDF 볼륨·숙제 테이블은 수정하지 않습니다. 배포 후 `homework-pdf-exam-verify`는 두 이미지 버전·기존 PDF 설정·새 제약·마이그레이션 기록을 확인합니다.

일부 페이지 추출본에는 본문·글꼴·이미지·회전·페이지 크기를 복사하며, 제외 페이지를 참조할 수 있는 링크·주석·입력 양식은 복사하지 않습니다. 주석이나 양식의 내용을 포함해야 하는 파일은 먼저 본문으로 저장해야 합니다. 비공개 볼륨 백업은 시험 테이블이 있으면 등록된 시험 PDF의 SHA-256도 함께 검증합니다.

숙제는 전체 또는 일부 페이지를, 시험은 겹치지 않는 문제·해설 페이지를 선택합니다. 선택 범위 합계는 20쪽이며 파일은 8MB 이하입니다. 시험 분석본은 문제 페이지 뒤에 해설 페이지를 모아 저장하며, 문항 위치는 문제 페이지 안으로 제한합니다. 파일은 교사 전용이며 학생 파일 제공 경로는 추가하지 않습니다. 해설에 명시된 정답·배점만 제안하고 누락값은 비워 두어 교사가 확인·입력합니다. 등록 후 정답관리에서 정답·답안 방식·배점을 수정하면 기존 재채점 흐름을 사용하며 문항 ID·번호·유형·학원·반은 고정합니다. 원본 조회에도 수정한 정답·배점을 표시합니다. 숙제와 시험의 일일·동시 분석 한도는 합산합니다. 코드 롤백 시 두 테이블과 파일은 보존하며, 등록 후 DB 삭제·복원은 별도 판단이 필요합니다.

`2026-11-13-01-add-homework-pdf-imports.sql`은 새 테이블 두 개를 추가합니다. 기존 숙제·학생별 검사 기록을 이관하거나 수정하지 않습니다. `ddl-auto=validate`를 사용하는 운영 환경에서는 **코드 배포 전에** 개발자가 백업 후 이 SQL을 적용·검증해야 합니다. 이미 테이블이 있거나 중간 실패했다면 재실행하지 말고 적용 상태를 확인합니다.

PDF 원본은 비공개 `homework-pdfs` 볼륨에 저장합니다. DB 백업만으로 원본 파일을 복구할 수 없으므로 운영 적용 전 DB와 볼륨을 함께 보존·복원하는 절차를 준비해야 합니다. 등록된 PDF 숙제와 문항 ID는 보존하며 문항 구성 교체·문항 수·관리 반 변경·삭제를 제한합니다. 다른 반에는 수업 배정으로 공유합니다. 등록 전 분석 초안은 분석 중 20분, 검토 가능 상태 24시간 후 정리합니다. 취소 초안도 정리 대상입니다. 등록 완료 PDF는 이 자동 정리에서 제외합니다.

기본값은 비활성화입니다. `HOMEWORK_PDF_ENABLED=true`, 쓰기 가능한 `HOMEWORK_PDF_STORAGE_DIR`, 기존 `OPENAI_API_KEY`가 필요합니다. 운영 Compose는 저장 경로를 비공개 볼륨으로 고정합니다. `HOMEWORK_PDF_MODEL` 기본값은 `gpt-6.1-sol`, 일일 분석 제한은 교사당 3건·전체 20건입니다. `HOMEWORK_PDF_DAILY_LIMIT`, `HOMEWORK_PDF_GLOBAL_DAILY_LIMIT`으로 범위 안에서 설정할 수 있습니다. PDF는 8MB·20쪽·60문항까지 처리하며 외부 AI로 원본과 페이지 이미지가 전송됩니다. 실제 모델 접근·과금·전송 범위를 검토한 뒤 활성화합니다.

로컬·테스트 프로필의 `app.homework-pdf.mock=true`는 실제 판독이나 외부 호출 없이 고정 가상 문항을 반환합니다. 운영 프로필에서는 이 설정을 허용하지 않습니다. 자동 제안은 확정 기록과 구분하며 교사가 문항 누락·번호·내용·유형을 확인한 뒤 등록합니다. 유형트리는 고정 ID와 버전으로 관리하고 분수가 포함된 유형 경로는 JSON 배열로 보존합니다.

코드만 되돌려도 이미 등록한 PDF·문항은 자동 삭제하지 않습니다. 신규 기록이 있는 DB의 테이블 삭제나 볼륨 제거는 복구 방법이 아니며 별도의 개발자 판단이 필요합니다.

운영자가 직접 운영 적용을 맡긴 경우 기존 `Build and Deploy`의 소유자 전용 수동 작업을 사용합니다. `expected_server_sha`로 운영 Git 버전을 고정하고 `homework-pdf-preflight` → `homework-pdf-migrate` → 코드 배포 → `homework-pdf-activate` → `homework-pdf-verify` 순서로 진행합니다. 마이그레이션은 점검한 운영 DB 식별값과 SQL SHA-256을 다시 확인하고, 비공개 서버 백업을 만든 후 제한된 임시 DB 계정으로 복원·무결성 검사·SQL 예행 검증을 수행해야 실제 신규 테이블을 만듭니다. 임시 DB와 계정만 정리하며 운영 테이블을 자동 삭제하거나 실패 SQL을 재시도하지 않습니다.

백업은 서버의 `/home/suhui/backups/homework-pdf/` 아래 권한 700 디렉터리에 보관하며 파일은 권한 600입니다. 운영 데이터나 자격 증명을 Actions 로그·아티팩트로 전송하지 않습니다. 배포 후 `homework-pdf-backup`은 DB 스냅샷과 원본 볼륨을 한 세트로 보관하고, 등록된 원본의 SHA-256 및 격리된 DB 복원을 검증합니다. 기존 DB 전용 백업만으로 PDF가 복구된다고 간주하지 않습니다. 새 도구는 기존 백업을 삭제하거나 보존 기한·외부 저장 설정을 바꾸지 않습니다.

복구할 때는 검증된 세트의 `manifest.json`과 체크섬, 대상 DB·볼륨을 먼저 확인합니다. 새 기능을 비활성화하고 쓰기를 중단한 뒤 개발자가 DB와 원본을 같은 세트로 복원합니다. 현재 업무 데이터가 덮어써지므로 복원 SQL이나 볼륨 교체를 자동으로 실행하지 않습니다. 코드만 되돌릴 경우 새 테이블과 원본을 보존하며 이전 코드는 추가 테이블을 사용하지 않습니다.

## 2026-10-20: 시험별 오답 원인 기록

오답 원인 기능 코드 배포 전에 `2026-10-20-04-add-submission-error-causes.sql`을 적용합니다.
`student_submissions`에 원인별 nullable 정수 5개와 재확인 상태를 추가합니다.
기존 제출의 원인 값은 `NULL`(미입력)을 유지하며, 기록된 0과 구분합니다.

```sql
SHOW COLUMNS FROM student_submissions LIKE 'error_cause%';
SHOW CREATE TABLE student_submissions;
```

음수 건수는 CHECK 제약으로 거부합니다. 답안 수정·재채점 후에는 원인을 보존하고
재확인 상태만 바꿉니다. 롤백은 기존 코드로 복귀한 뒤
`2026-10-20-04-add-submission-error-causes_rollback.sql`로 수행하며, 원인 기록이 삭제됩니다.
실제 운영 DB에는 이 작업에서 적용하지 않습니다.

## 2026-10-20: 하나의 숙제를 여러 수업에 배정

적용 파일은 **`2026-10-20-01-share-homeworks-across-lessons.sql` 한 개**입니다. 이전의 01 → 02 → 03 초안을 최종 구조로 합쳤습니다. 숙제 내용은 공유하고, 진도·채점·질문·재제출 표시는 수업별로 관리합니다.

| 대상 | 변경 |
| --- | --- |
| `lesson_homeworks` | 숙제와 여러 수업의 연결 |
| `student_lessons` | 해당 수업의 학생별 숙제 배정 |
| `student_homeworks` | 원본 수업 및 학생·숙제·수업별 유일 제약 |
| `student_homework_classes` | 배정 해제 후에도 유지할 반별 기록 접근 범위 |
| `clinic_homework_progress` | 학생 숙제 기록별 스냅샷 연결·유일 제약, 기존 학생·숙제 단위 유일 키 교체 |

MySQL 8.0.16 이상에서 기존 숙제 공유 초안이 **미적용인 DB**에 한 번만 실행합니다. 백엔드 배포 전에 DB를 백업하고 애플리케이션의 쓰기를 중지합니다. `mysql --force`는 사용하지 않습니다.

```bash
mysql -u <user> -p <database> < migrations/2026-10-20-01-share-homeworks-across-lessons.sql
```

SQL 끝의 검증 결과 `issue_count` 네 개가 모두 `0`인지 확인한 뒤 새 백엔드를 시작합니다. 기존 숙제·학생 숙제 행을 복제하거나 삭제하지 않으며 채점·질문·재제출 표시, 피드백·출결·작성자·AI 여부와 기존 생성·수정 시각을 유지합니다. 수업 없는 기존 학생 숙제는 `lesson_id = NULL`로 남기며 생성 컬럼 `lesson_scope_id = 0`으로 중복을 막습니다. 다른 반의 클리닉 스냅샷이나 대응 학생 숙제가 없는 스냅샷은 기존 값을 유지하고 새 기록에 연결하지 않습니다.

다음 경우에는 영구 테이블 변경 전에 중단합니다.

- 기존 초안의 테이블·컬럼·학생/숙제 유일 키가 있거나 이미 최종 SQL을 적용한 경우
- 같은 학생·숙제 기록이 중복인 경우
- 같은 학생·수업에 숙제가 두 개 이상인 경우
- 같은 클리닉·학생·숙제 스냅샷이 중복인 경우

제약 이름 `ck_homework_migration_*`이 중단 원인을 가리킵니다. 중복 데이터는 다음 쿼리로 확인합니다. 실제 기록을 확인해 정리한 뒤 실행하며, 임의로 병합하거나 채점을 여러 수업에 복사하지 않습니다.

```sql
SELECT student_id, homework_id, COUNT(*) AS record_count
FROM student_homeworks GROUP BY student_id, homework_id HAVING COUNT(*) > 1;

SELECT sh.student_id, h.lesson_id, COUNT(*) AS assignment_count
FROM student_homeworks sh JOIN homeworks h ON h.id = sh.homework_id
WHERE h.lesson_id IS NOT NULL
GROUP BY sh.student_id, h.lesson_id HAVING COUNT(*) > 1;

SELECT clinic_id, student_id, homework_id, COUNT(*) AS snapshot_count
FROM clinic_homework_progress GROUP BY clinic_id, student_id, homework_id HAVING COUNT(*) > 1;
```

이전 01/02/03 초안을 이미 적용한 DB는 통합 파일을 실행하지 않고 실제 스키마와 적용 이력을 먼저 대조합니다. MySQL DDL은 자동 커밋되므로 중간 실패 시 SQL 전체가 롤백되지 않습니다. 데이터 이관은 DDL 뒤 별도 트랜잭션으로 처리합니다. 오류가 나면 서비스를 계속 중지한 상태에서 백업 복구 또는 적용된 DDL 확인 후 복구하며 파일을 처음부터 다시 실행하지 않습니다.

IDE에서 문장별로 실행할 때는 같은 DB 연결을 유지합니다. 데이터 이관의 UPDATE에는 아직 이관되지 않은 행만 선택하는 WHERE가 있습니다. IDE의 안전 검사에서 UPDATE가 차단된 경우 연결·트랜잭션이 유지되어 있다면 수정한 해당 문장부터 이어서 실행합니다. 이미 실행한 DDL부터 다시 시작하지 않습니다. 연결이 끊겼거나 ROLLBACK했다면 데이터 이관 상태를 먼저 확인합니다.

롤백은 **`2026-10-20-01-share-homeworks-across-lessons_rollback.sql` 한 개**입니다. 서비스 중지 후 사용하며, 신규 공유 연결·원본 수업·명시적 배정 해제·반 이력 또는 기존 클리닉 유일 제약과 충돌하는 스냅샷이 있으면 테이블 변경 전에 중단합니다. 기존 구조로 표현할 수 있는 상태에서만 스키마를 되돌립니다. 이관 중 새로 생긴 빈 `student_lessons` 행과 이후 수정된 채점·피드백까지 되돌리는 데이터 복구는 아니며, 배포 전 상태의 완전한 복구에는 백업이 필요합니다.

```bash
mysql -u <user> -p <database> < migrations/2026-10-20-01-share-homeworks-across-lessons_rollback.sql
```

개발용 MySQL의 회귀 검증은 `python3 migrations/tests/test_homework_sharing.py --container <test-mysql-container>`로 실행합니다. 이 명령은 해당 컨테이너의 root 무암호 접속을 사용해 임시 DB만 생성·제거하므로 테스트 전용 컨테이너에서 실행합니다. 실제 운영 데이터 분포와 DDL 소요 시간·잠금 영향은 별도 확인이 필요합니다.

## 2026-10-20: AI 피드백 이전 시험 반영 개수

코드 배포 전에 `2026-10-20-01-add-feedback-score-history-count.sql`을 적용합니다.
`feedback_prompt_templates.score_history_count`는 선생님별 성적 이력 반영 개수이며,
기존 설정은 기본값 3으로 채워집니다. 0은 추이 반영 끄기, 1~10은 이번 시험을 제외한
이전 시험 최대 반영 개수입니다. 커스텀 프롬프트 활성화 여부와 관계없이 적용됩니다.

적용 후 컬럼의 기본값이 3이고 검증 쿼리의 `invalid_score_history_count`가 0인지 확인합니다.
롤백 파일은 이전 코드로 복귀한 후 실행하며 저장된 반영 개수 설정을 삭제합니다.

## 2026-09-20: 학생 계정 통합 감사 이력

반별로 따로 생성했던 중복 학생 ID를 한 대표 학생 ID로 통합할 때, 삭제된 원본 ID와
대표 ID의 매핑을 남기는 `student_account_merges` 테이블을 추가합니다. 개인정보를 중복
저장하지 않고 ID, 실행 관리자, 실행 시각만 보관합니다.

운영 환경은 `ddl-auto: validate`이므로 **학생 계정 통합 코드 배포 전에** 적용해야 합니다.

```bash
mysql -u root -p academy < migrations/2026-09-20-01-add-student-account-merges.sql
```

적용 후 확인:

```sql
SHOW CREATE TABLE student_account_merges \G
SHOW INDEX FROM student_account_merges;
```

실제 계정 통합을 한 뒤에는 롤백 SQL만으로 삭제된 학생 계정과 이동된 학습 기록을
복원할 수 없습니다. 통합 실행 전 DB 백업을 만들고, 테이블 롤백은 통합 이력이 전혀
없을 때만 실행합니다.

```bash
mysql -u root -p academy < migrations/2026-09-20-01-add-student-account-merges_rollback.sql
```

## 2026-08-29: 학생-반 다중 소속

학생 계정과 반 소속을 분리하는 `student_class_enrollments` 테이블을 추가합니다.
기존 `students.class_id`는 전환 기간의 호환 컬럼으로 유지하며, 기존 학생은 반이
운영 중이면 `ACTIVE`, 종강했으면 `COMPLETED` 상태로 백필됩니다.

운영 코드 배포 전에 적용하고, 파일 마지막의 검증 쿼리 두 결과가 모두 `0`인지 확인합니다.

```bash
mysql -u root -p academy < migrations/2026-08-29-01-add-student-class-enrollments.sql
```

적용 후 추가 확인:

```sql
SHOW CREATE TABLE student_class_enrollments \G
SELECT status, COUNT(*) FROM student_class_enrollments GROUP BY status;
```

롤백은 다중 반 소속과 이력 데이터가 아직 필요 없을 때만 실행합니다.

```bash
mysql -u root -p academy < migrations/2026-08-29-01-add-student-class-enrollments_rollback.sql
```

## 2026-08-24: 시험 미입력 답안 허용

학생이 답하지 않은 문항을 `student_submission_details.student_answer = NULL`로 저장하고
자동채점 문항에서 오답으로 처리할 수 있도록 해당 컬럼의 `NOT NULL` 제약을 제거합니다.
운영 코드 배포 전에 적용합니다.

```bash
mysql -u root -p academy < migrations/2026-08-24-01-allow-null-student-answers.sql
```

적용 후 확인:

```sql
SHOW COLUMNS FROM student_submission_details LIKE 'student_answer';
```

롤백 SQL은 기존 `NULL` 답안을 빈 문자열로 바꾼 뒤 `NOT NULL` 제약을 복원합니다.

```bash
mysql -u root -p academy < migrations/2026-08-24-01-allow-null-student-answers_rollback.sql
```

## 2026-08-22: 반 종강 상태와 시험 객관식 복수정답

학생·수업·성적 이력을 삭제하지 않고 반을 종강 처리할 수 있도록
`academy_classes.ended_at`을 추가합니다. 객관식 문항에서 여러 보기를 하나의 정답
집합으로 채점할 수 있도록 `test_questions.multiple_answers`도 추가합니다.

운영 환경은 `ddl-auto: validate`이므로 **코드 배포 전에 두 파일을 순서대로** 적용해야 합니다.

```bash
mysql -u root -p academy < migrations/2026-08-22-01-add-class-ended-at.sql
mysql -u root -p academy < migrations/2026-08-22-02-add-test-question-multiple-answers.sql
```

적용 후 확인:

```sql
SHOW COLUMNS FROM academy_classes LIKE 'ended_at';
SHOW COLUMNS FROM test_questions LIKE 'multiple_answers';
SELECT COUNT(*) AS unexpected_multiple_answers
FROM test_questions
WHERE multiple_answers NOT IN (0, 1) OR multiple_answers IS NULL;
```

롤백은 기능 사용 데이터가 더는 필요 없을 때만 역순으로 수행합니다. 종강 상태와 복수정답
설정을 보존해야 한다면 먼저 해당 컬럼을 별도 백업합니다.

```bash
mysql -u root -p academy < migrations/2026-08-22-02-add-test-question-multiple-answers_rollback.sql
mysql -u root -p academy < migrations/2026-08-22-01-add-class-ended-at_rollback.sql
```

## 2026-08-16: 학생 새 UI 의견 테이블

새 학생 UI에서 보낸 만족도, 불편 유형, 자유 의견과 화면·기기 메타데이터를 저장합니다.
운영 환경은 `ddl-auto: validate`이므로 **코드 배포 전에** 적용해야 합니다.

```bash
mysql -u root -p academy < migrations/2026-08-16-01-add-student-ui-feedback.sql
```

적용 후 `SHOW CREATE TABLE student_ui_feedback \G`로 두 외래 키와 학생/학원별 인덱스를
확인합니다. 롤백은 아직 수집된 의견을 보존할 필요가 없을 때만 아래 파일로 수행합니다.

```bash
mysql -u root -p academy < migrations/2026-08-16-01-add-student-ui-feedback_rollback.sql
```

## 2026-08-10: 시험 문항 수학 유형 스냅샷

시험 문항이 교재의 `topic`을 실시간 참조하지 않고 출제 시점 값을 독립 보관하도록
`test_questions.topic`, `topic_l1..topic_l5`를 추가합니다. 기존 교재 연결 문항은 현재
교재 유형으로 백필됩니다.

운영 환경은 `ddl-auto: validate`이므로 **코드 배포 전에** 적용해야 합니다.

```bash
mysql -u root -p academy < migrations/2026-08-10-01-add-test-question-topic-snapshot.sql
```

적용 후 확인:

```sql
SELECT COUNT(*) AS missing_snapshot
FROM test_questions tq
JOIN textbook_problems tp ON tp.id = tq.textbook_problem_id
WHERE tp.topic IS NOT NULL AND tp.topic <> '' AND tq.topic IS NULL;
```

결과가 `0`이어야 합니다. 롤백 SQL은 새 컬럼과 시험에서 수정한 유형 값을 삭제하므로,
운영 데이터 입력이 시작된 뒤에는 DB 백업을 복원하는 방식을 우선합니다.

## 2026-08-08: 학생 알림함 테이블

학생별 알림 목록, 읽음 상태, 이동 경로를 저장하는 `student_notifications` 테이블을
생성합니다. 동일 이벤트의 중복 생성을 막는 `(student_id, source_key)` 고유 인덱스와
알림함/미확인 조회 인덱스를 포함합니다. 운영 환경은 `ddl-auto: validate`이므로
**알림 기능이 포함된 코드 배포 전에** 적용해야 합니다.

```bash
mysql -u root -p academy < migrations/2026-08-08-01-add-student-notifications.sql
```

적용 후 확인:

```sql
SHOW CREATE TABLE student_notifications \G
SHOW INDEX FROM student_notifications;
```

롤백은 아직 보존할 알림이 없을 때만 수행합니다.

```bash
mysql -u root -p academy < migrations/2026-08-08-01-add-student-notifications_rollback.sql
```

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
