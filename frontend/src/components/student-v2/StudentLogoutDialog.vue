<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
</script>

<template>
  <el-dialog
    v-model="visible"
    class="student-logout-dialog"
    modal-class="student-logout-overlay"
    width="420px"
    :show-close="false"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    append-to-body
    destroy-on-close
  >
    <div class="student-logout-dialog__content">
      <span class="student-logout-dialog__icon" aria-hidden="true">
        <el-icon><SwitchButton /></el-icon>
      </span>
      <p class="student-logout-dialog__eyebrow">ACCOUNT</p>
      <h2>로그아웃할까요?</h2>
      <p class="student-logout-dialog__description">
        현재 기기에서 로그아웃됩니다. 다시 이용하려면 학생 ID와 PIN으로 로그인해야 해요.
      </p>
      <div class="student-logout-dialog__notice">
        <el-icon><CircleCheck /></el-icon>
        <span>학습 기록과 제출 내용은 그대로 보관돼요.</span>
      </div>
    </div>

    <template #footer>
      <div class="student-logout-dialog__footer">
        <el-button :disabled="loading" @click="visible = false">계속 이용하기</el-button>
        <el-button
          class="student-logout-dialog__confirm"
          :loading="loading"
          @click="emit('confirm')"
        >
          로그아웃
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style>
.student-logout-overlay {
  background: var(--student-overlay);
  backdrop-filter: blur(3px);
}
.student-logout-overlay .el-overlay-dialog {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.student-logout-dialog.el-dialog {
  max-width: calc(100vw - 32px);
  margin: 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: calc(var(--student-radius-lg) + var(--student-space-1));
  background: var(--student-surface);
  box-shadow: var(--student-shadow-dialog);
}
.student-logout-dialog .el-dialog__header {
  display: none;
}
.student-logout-dialog .el-dialog__body {
  padding: 26px 24px 20px;
}
.student-logout-dialog .el-dialog__footer {
  padding: 0 24px 24px;
}
.student-logout-dialog__content {
  text-align: center;
}
.student-logout-dialog__icon {
  display: grid;
  width: 54px;
  height: 54px;
  margin: 0 auto 15px;
  place-items: center;
  border-radius: 17px;
  color: var(--student-danger);
  background: var(--student-danger-soft);
  font-size: 25px;
  box-shadow: inset 0 0 0 1px rgba(185, 45, 61, 0.08);
}
.student-logout-dialog__eyebrow {
  margin: 0 0 5px;
  color: var(--student-danger);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
}
.student-logout-dialog__content h2 {
  margin: 0;
  color: var(--student-ink);
  font-size: var(--student-font-title-md);
  font-weight: 850;
  letter-spacing: -0.035em;
}
.student-logout-dialog__description {
  max-width: 330px;
  margin: 11px auto 0;
  color: var(--student-muted);
  font-size: var(--student-font-md);
  line-height: 1.6;
  word-break: keep-all;
}
.student-logout-dialog__notice {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 14px;
  color: var(--student-text-subtle);
  background: var(--student-surface-soft);
  font-size: var(--student-font-sm);
  font-weight: 650;
  line-height: 1.45;
}
.student-logout-dialog__notice .el-icon {
  flex: 0 0 auto;
  color: var(--student-primary);
  font-size: 16px;
}
.student-logout-dialog__footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.student-logout-dialog__footer .el-button {
  min-height: var(--student-control-height-md);
  margin: 0;
  border-color: var(--student-border);
  border-radius: var(--student-radius-md);
  color: var(--student-text);
  font-size: var(--student-font-body);
  font-weight: 800;
}
.student-logout-dialog__footer .student-logout-dialog__confirm {
  border-color: var(--student-danger);
  color: var(--student-surface);
  background: var(--student-danger);
  box-shadow: 0 8px 18px rgba(185, 45, 61, 0.2);
}
.student-logout-dialog__footer .student-logout-dialog__confirm:hover,
.student-logout-dialog__footer .student-logout-dialog__confirm:focus-visible {
  border-color: var(--student-danger-strong);
  background: var(--student-danger-strong);
}
.student-logout-dialog__footer .el-button:focus-visible {
  outline: 0;
  box-shadow: var(--student-focus-ring);
  outline-offset: 2px;
}
.student-logout-dialog__footer .student-logout-dialog__confirm:focus-visible {
  outline-color: rgba(185, 45, 61, 0.25);
}
@media (max-width: 600px) {
  .student-logout-overlay .el-overlay-dialog {
    align-items: flex-end;
    padding: 0;
  }
  .student-logout-dialog.el-dialog {
    width: 100% !important;
    max-width: none;
    border-width: 1px 0 0;
    border-radius: 24px 24px 0 0;
  }
  .student-logout-dialog .el-dialog__body {
    padding: 24px 20px 18px;
  }
  .student-logout-dialog .el-dialog__footer {
    padding: 0 20px calc(18px + env(safe-area-inset-bottom));
  }
}
@media (max-width: 350px) {
  .student-logout-dialog .el-dialog__body {
    padding-right: 16px;
    padding-left: 16px;
  }
  .student-logout-dialog .el-dialog__footer {
    padding-right: 16px;
    padding-left: 16px;
  }
  .student-logout-dialog__footer {
    grid-template-columns: 1fr;
  }
  .student-logout-dialog__footer .el-button {
    min-height: 46px;
  }
}
</style>
