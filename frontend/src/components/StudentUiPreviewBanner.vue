<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useStudentUiMode } from '@/composables/useStudentUiMode'

const router = useRouter()
const { enablePreview } = useStudentUiMode()

const startPreview = async () => {
  enablePreview()
  await router.replace('/student/dashboard')
}
</script>

<template>
  <section class="student-ui-preview-banner" aria-labelledby="student-ui-preview-title">
    <span class="student-ui-preview-banner__icon"><el-icon><MagicStick /></el-icon></span>
    <div class="student-ui-preview-banner__content">
      <span class="student-ui-preview-banner__eyebrow">BETA</span>
      <strong id="student-ui-preview-title">새로운 학생 화면을 체험해 보세요</strong>
      <small>언제든 기존 화면으로 돌아올 수 있어요.</small>
    </div>
    <el-button type="primary" @click="startPreview">새 UI 체험하기</el-button>
  </section>
</template>

<style scoped>
.student-ui-preview-banner {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #c6d8ff;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f7ff, #eef7ff);
}
.student-ui-preview-banner__icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 13px; color: #2457d6; background: #dfe9ff; font-size: 20px; }
.student-ui-preview-banner__content { display: grid; min-width: 0; gap: 3px; }
.student-ui-preview-banner__eyebrow { color: #2457d6; font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.student-ui-preview-banner__content strong { color: #25324a; font-size: 14px; line-height: 1.35; }
.student-ui-preview-banner__content small { color: #667085; font-size: 12px; }
@media (max-width: 560px) {
  .student-ui-preview-banner { grid-template-columns: auto minmax(0, 1fr); }
  .student-ui-preview-banner .el-button { grid-column: 1 / -1; width: 100%; min-height: 44px; }
}
</style>
