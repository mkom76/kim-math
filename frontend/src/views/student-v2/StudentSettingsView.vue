<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { usePinChange } from '@/composables/usePinChange'

const router = useRouter()
const authStore = useAuthStore()
const { loading, pinForm, pinLength, handleChangePIN } = usePinChange()

async function logout() {
  try {
    await ElMessageBox.confirm('로그아웃할까요?', '로그아웃', {
      confirmButtonText: '로그아웃',
      cancelButtonText: '취소',
      type: 'warning',
    })
    await authStore.logout()
    await router.replace('/login')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error('로그아웃하지 못했습니다')
  }
}
</script>

<template>
  <div class="student-page settings-page">
    <header class="student-page__header settings-header">
      <div>
        <p class="student-page__eyebrow">ACCOUNT</p>
        <h1 class="student-page__title">설정</h1>
        <p class="student-page__subtitle">계정과 개인정보를 안전하게 관리해요.</p>
      </div>
      <button type="button" class="student-icon-button" aria-label="홈으로 돌아가기" @click="router.push('/student/dashboard')">
        <el-icon><Close /></el-icon>
      </button>
    </header>

    <section class="settings-section" aria-labelledby="privacy-title">
      <div class="settings-section__heading">
        <p>개인정보 및 지원</p>
        <h2 id="privacy-title">서비스 정보</h2>
      </div>
      <div class="settings-surface">
        <button type="button" class="settings-row" @click="router.push('/privacy')">
          <span class="settings-row__icon"><el-icon><Document /></el-icon></span>
          <span class="settings-row__content">
            <strong>개인정보처리방침</strong>
            <small>수집 정보와 이용 목적을 확인해요.</small>
          </span>
          <el-icon class="settings-row__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <section class="settings-section" aria-labelledby="pin-title">
      <div class="settings-section__heading">
        <p>보안</p>
        <h2 id="pin-title">PIN 변경</h2>
      </div>
      <div class="settings-surface pin-panel" v-loading="loading">
        <label>
          <span>현재 PIN</span>
          <el-input v-model="pinForm.currentPin" type="password" inputmode="numeric" placeholder="현재 PIN을 입력하세요" :maxlength="pinLength" show-password />
        </label>
        <label>
          <span>새 PIN</span>
          <el-input v-model="pinForm.newPin" type="password" inputmode="numeric" :placeholder="`숫자 ${pinLength}자리`" :maxlength="pinLength" show-password />
        </label>
        <label>
          <span>새 PIN 확인</span>
          <el-input v-model="pinForm.confirmPin" type="password" inputmode="numeric" placeholder="새 PIN을 다시 입력하세요" :maxlength="pinLength" show-password @keyup.enter="handleChangePIN" />
        </label>
        <p class="pin-panel__help"><el-icon><Lock /></el-icon> PIN은 로그인에 사용되며 주기적인 변경을 권장해요.</p>
        <el-button type="primary" class="pin-panel__submit" :loading="loading" @click="handleChangePIN">PIN 변경하기</el-button>
      </div>
    </section>

    <section class="settings-surface account-panel" aria-label="계정 작업">
      <button type="button" class="settings-row settings-row--danger" @click="logout">
        <span class="settings-row__icon"><el-icon><SwitchButton /></el-icon></span>
        <span class="settings-row__content"><strong>로그아웃</strong><small>현재 기기에서 계정을 종료해요.</small></span>
        <el-icon class="settings-row__arrow"><ArrowRight /></el-icon>
      </button>
    </section>
  </div>
</template>

<style scoped>
.settings-page { display: grid; min-width: 0; gap: 28px; }
.settings-header { margin-bottom: 0; }
.settings-section { display: grid; gap: 12px; }
.settings-section__heading p { margin: 0 0 3px; color: var(--student-primary); font-size: 11px; font-weight: 750; letter-spacing: .06em; }
.settings-section__heading h2 { margin: 0; color: var(--student-ink); font-size: 19px; font-weight: 850; letter-spacing: -.02em; }
.settings-surface { overflow: hidden; border: 1px solid var(--student-border); border-radius: var(--student-radius-lg); background: var(--student-surface); box-shadow: var(--student-shadow-soft); }
.settings-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) 20px; align-items: center; gap: 12px; width: 100%; min-width: 0; min-height: 72px; padding: 12px 16px; border: 0; color: inherit; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.settings-row__icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 13px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 20px; }
.settings-row__content { display: grid; min-width: 0; gap: 3px; }
.settings-row__content strong { color: var(--student-ink); font-size: 14px; font-weight: 800; }
.settings-row__content small { color: var(--student-muted); font-size: 12px; line-height: 1.45; overflow-wrap: anywhere; }
.settings-row__arrow { color: var(--student-muted); }
.pin-panel { display: grid; gap: 17px; padding: 18px; overflow: visible; }
.pin-panel label { display: grid; gap: 8px; color: var(--student-text); font-size: 13px; font-weight: 750; }
.pin-panel__help { display: flex; align-items: flex-start; gap: 7px; margin: 0; color: var(--student-muted); font-size: 12px; line-height: 1.5; }
.pin-panel__help .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--student-primary); }
.pin-panel__submit { width: 100%; min-height: 52px; margin: 0; border-radius: 15px; font-size: 15px; font-weight: 800; }
.account-panel { margin-top: -4px; }
.settings-row--danger .settings-row__icon { color: var(--student-danger); background: var(--student-danger-soft); }
.settings-row--danger .settings-row__content strong { color: var(--student-danger); }
@media (max-width: 360px) {
  .settings-row { grid-template-columns: 40px minmax(0, 1fr) 18px; padding: 11px 13px; }
  .settings-row__icon { width: 40px; height: 40px; }
}
</style>
