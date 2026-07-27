<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const router = useRouter()
const contactEmail = (import.meta.env.VITE_PRIVACY_CONTACT_EMAIL || '').trim()
const contactLabel = computed(() =>
  contactEmail || '소속 학원 관리자 또는 Play 스토어에 표시된 개발자 연락처',
)
</script>

<template>
  <main class="privacy-page">
    <article class="privacy-card">
      <header class="privacy-header">
        <el-button :icon="ArrowLeft" text @click="router.back()">뒤로</el-button>
        <div>
          <p class="eyebrow">킴매스 학생</p>
          <h1>개인정보처리방침</h1>
          <p class="effective-date">시행일: 2026년 7월 26일</p>
        </div>
      </header>

      <p>
        킴매스 및 서비스를 이용하는 소속 학원(이하 “운영자”)은 학생의 학습 관리와
        학부모 안내에 필요한 범위에서 개인정보를 처리합니다. 운영자는 관련 법령을
        준수하며, 아래 목적 이외의 용도로 개인정보를 이용하지 않습니다.
      </p>

      <section>
        <h2>1. 처리하는 개인정보</h2>
        <ul>
          <li>학생 정보: 이름, 학년, 학교, 학생 연락처(선택), 소속 학원·반</li>
          <li>보호자 정보: 보호자 이름과 연락처</li>
          <li>계정·보안 정보: 학생 ID, 암호화된 PIN, 로그인 기록, 세션 정보</li>
          <li>학습 정보: 시험 답안·점수·오답, 숙제, 출결, 수업 피드백, 영상 학습 진도</li>
          <li>
            앱 정보: 앱 플랫폼 및 버전, YouTube 영상 재생 시 기기 무결성 증명 정보,
            푸시 기능 활성화 시 알림용 기기 토큰
          </li>
        </ul>
      </section>

      <section>
        <h2>2. 이용 목적</h2>
        <ul>
          <li>학생 본인 확인과 로그인 상태 유지</li>
          <li>수업, 시험, 숙제, 출결, 클리닉 및 학습 진도 관리</li>
          <li>학생·보호자에게 학습 피드백과 알림 제공</li>
          <li>서비스 보안, 장애 대응 및 부정 이용 방지</li>
        </ul>
      </section>

      <section>
        <h2>3. 보유 기간</h2>
        <p>
          개인정보는 학습 관리 서비스 제공에 필요한 기간 동안 보관하고, 처리 목적이
          달성되거나 삭제 요청 처리가 완료되면 지체 없이 안전하게 삭제합니다. 법령상
          보존 의무가 있거나 진행 중인 분쟁·민원 처리에 필요한 기록은 다른 정보와
          분리하여 해당 법정 또는 처리 기간까지만 보관한 뒤 삭제합니다.
        </p>
      </section>

      <section>
        <h2>4. 외부 서비스 이용</h2>
        <ul>
          <li>
            OpenAI: 교사가 AI 피드백 생성을 요청한 경우 학생 이름, 시험·숙제 결과와
            교사 피드백 일부가 피드백 문안 생성을 위해 전송될 수 있습니다.
          </li>
          <li>
            Google Firebase Cloud Messaging: 푸시 알림 기능이 활성화된 버전에서는
            알림 전달을 위해 기기 토큰과 알림 전송 정보가 처리될 수 있습니다.
          </li>
          <li>
            YouTube: 교사가 등록한 해설 영상을 재생할 때 YouTube의 개인정보처리방침이
            적용될 수 있습니다.
          </li>
        </ul>
        <p>
          운영자는 위 서비스를 개인정보 처리 목적에 필요한 범위에서만 사용하며,
          서비스 제공자의 보안·개인정보 보호 조건을 확인합니다.
        </p>
      </section>

      <section>
        <h2>5. 아동·청소년의 개인정보</h2>
        <p>
          학생 계정은 학원이 등록하며, 필요한 경우 보호자 동의를 받은 후 활성화됩니다.
          보호자는 학원에 학생 정보의 열람, 정정, 동의 철회 또는 삭제를 요청할 수
          있습니다.
        </p>
      </section>

      <section>
        <h2>6. 이용자의 권리와 삭제 요청</h2>
        <p>
          앱 안에서는 사용자가 직접 계정을 만들지 않습니다. 학생 또는 보호자는 소속
          학원 관리자에게 개인정보 열람·정정·삭제 및 처리 정지를 요청할 수 있습니다.
          요청자의 신원을 확인한 뒤 학생·보호자 기본정보, 계정·세션 정보, 시험·숙제·출결·
          피드백·영상 진도 등 학습 기록을 삭제합니다. 동의 증빙이나 법령상 보존이
          필요한 기록은 위 보유 기간에 따라 분리 보관할 수 있습니다.
        </p>
      </section>

      <section>
        <h2>7. 안전성 확보 조치</h2>
        <p>
          운영자는 HTTPS 암호화 통신, PIN 단방향 암호화, 역할 기반 접근 제어, 로그인
          잠금, 학원별 데이터 분리와 서버 접근 제한 등 개인정보 보호 조치를 적용합니다.
        </p>
      </section>

      <section>
        <h2>8. 문의</h2>
        <p>
          개인정보 관련 문의나 삭제 요청은
          <a v-if="contactEmail" :href="`mailto:${contactEmail}`">{{ contactLabel }}</a>
          <strong v-else>{{ contactLabel }}</strong>
          로 보내 주세요.
        </p>
      </section>

      <section>
        <h2>9. 방침 변경</h2>
        <p>
          본 방침이 변경되는 경우 시행 전에 서비스 화면 또는 적절한 방법으로
          안내합니다.
        </p>
      </section>
    </article>
  </main>
</template>

<style scoped>
.privacy-page {
  min-height: 100dvh;
  padding: 24px 16px 48px;
  background: #f5f7fa;
  color: #303133;
}

.privacy-card {
  max-width: 840px;
  margin: 0 auto;
  padding: 32px;
  border: 1px solid #e4e7ed;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 30px rgb(31 45 61 / 8%);
  line-height: 1.75;
}

.privacy-header {
  display: grid;
  gap: 16px;
  margin-bottom: 28px;
}

.privacy-header .el-button {
  width: fit-content;
  padding-left: 0;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2f80ed;
  font-size: 14px;
  font-weight: 700;
}

h1 {
  margin: 0;
  font-size: clamp(28px, 6vw, 40px);
  line-height: 1.2;
}

.effective-date {
  margin: 8px 0 0;
  color: #909399;
  font-size: 14px;
}

section {
  margin-top: 28px;
}

h2 {
  margin-bottom: 8px;
  font-size: 19px;
}

p,
ul {
  margin: 0;
}

ul {
  padding-left: 22px;
}

li + li {
  margin-top: 5px;
}

a {
  color: #2f80ed;
  font-weight: 600;
}

@media (max-width: 600px) {
  .privacy-card {
    padding: 22px 18px;
    border-radius: 12px;
  }
}
</style>
