<script setup lang="ts">
import { computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ autoOpen?: boolean }>()
const visible = defineModel<boolean>({ default: false })
const auth = useAuthStore()
const storageKey = computed(() =>
  auth.userId ? `kim-math:student-guide:2026-11:${auth.userId}` : null,
)

watch(
  storageKey,
  (key) => {
    if (!props.autoOpen || !key) return
    try {
      visible.value = window.localStorage.getItem(key) !== 'seen'
    } catch {
      visible.value = true
    }
  },
  { immediate: true },
)

function acknowledge() {
  if (storageKey.value) {
    try {
      window.localStorage.setItem(storageKey.value, 'seen')
    } catch {
      /* Storage may be disabled. */
    }
  }
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="학생 화면이 새로워졌어요"
    class="student-guide-dialog"
    modal-class="student-guide-overlay"
    width="min(520px, calc(100vw - 32px))"
    align-center
    append-to-body
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <p class="guide-intro">필요한 학습 메뉴를 더 쉽게 찾을 수 있도록 정리했어요.</p>
    <dl class="guide-list">
      <div>
        <dt>홈</dt>
        <dd>수업 피드백과 영상, 최근 시험 점수와 숙제 완성도를 확인해요.</dd>
      </div>
      <div>
        <dt>시험</dt>
        <dd>응시할 시험과 지난 시험 결과를 확인해요.</dd>
      </div>
      <div>
        <dt>클리닉 신청</dt>
        <dd>클리닉 일정과 신청 내역을 확인해요.</dd>
      </div>
      <div>
        <dt>더보기</dt>
        <dd>학습 통계, Q&A, 알림과 설정을 모아 두었어요.</dd>
      </div>
    </dl>
    <p class="guide-note">이 안내는 설정에서 언제든 다시 볼 수 있어요.</p>
    <template #footer
      ><button type="button" class="guide-confirm" @click="acknowledge">알겠어요</button></template
    >
  </el-dialog>
</template>

<style scoped>
.guide-intro {
  margin: 0;
  color: var(--student-text);
  font-size: var(--student-font-body);
  line-height: 1.6;
}
.guide-list {
  display: grid;
  gap: var(--student-space-4);
  margin: var(--student-space-5) 0;
}
.guide-list > div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: var(--student-space-3);
}
.guide-list dt {
  color: var(--student-primary);
  font-size: var(--student-font-body);
  font-weight: 700;
}
.guide-list dd {
  margin: 0;
  color: var(--student-text);
  font-size: var(--student-font-body);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.guide-note {
  margin: 0;
  color: var(--student-muted);
  font-size: var(--student-font-md);
}
.guide-confirm {
  width: 100%;
  min-height: 48px;
  padding: 12px 16px;
  border: 0;
  border-radius: var(--student-radius-sm);
  color: var(--student-surface);
  background: var(--student-primary);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.guide-confirm:focus-visible {
  outline: 3px solid var(--student-primary);
  outline-offset: 3px;
}
@media (max-width: 350px) {
  .guide-list > div {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>

<style>
.student-guide-dialog.el-dialog {
  border-radius: var(--student-radius-lg);
  max-height: calc(100dvh - 32px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  overflow-y: auto;
  background: var(--student-surface);
}
.student-guide-dialog .el-dialog__title {
  color: var(--student-ink);
  font-size: var(--student-font-title-sm);
  font-weight: 700;
}
.student-guide-dialog .el-dialog__footer {
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}
</style>
