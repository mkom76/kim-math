<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  studentUiFeedbackAPI,
  type StudentUiFeedbackCategory,
  type StudentUiFeedbackSentiment,
} from '@/api/client'
import { platformName } from '@/utils/platform'
import packageMetadata from '../../../package.json'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const route = useRoute()
const submitting = ref(false)
const form = reactive<{
  sentiment?: StudentUiFeedbackSentiment
  category?: StudentUiFeedbackCategory
  message: string
}>({ message: '' })

const sentiments: Array<{ value: StudentUiFeedbackSentiment; emoji: string; label: string }> = [
  { value: 'POSITIVE', emoji: '🙂', label: '좋아요' },
  { value: 'IMPROVEMENT', emoji: '😕', label: '불편해요' },
  { value: 'BUG', emoji: '🛠️', label: '오류가 있어요' },
]

const categories: Array<{ value: StudentUiFeedbackCategory; label: string }> = [
  { value: 'NAVIGATION', label: '메뉴·이동' },
  { value: 'LAYOUT', label: '화면 배치' },
  { value: 'READABILITY', label: '글자·색상' },
  { value: 'PERFORMANCE', label: '속도·반응' },
  { value: 'CONTENT', label: '문구·정보' },
  { value: 'OTHER', label: '기타' },
]

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const needsCategory = computed(() => form.sentiment === 'IMPROVEMENT' || form.sentiment === 'BUG')
const canSubmit = computed(
  () => Boolean(form.sentiment) && (!needsCategory.value || Boolean(form.category)),
)

watch(
  () => form.sentiment,
  (value) => {
    if (value === 'POSITIVE') form.category = undefined
  },
)

function resetForm() {
  form.sentiment = undefined
  form.category = undefined
  form.message = ''
}

async function submit() {
  if (!form.sentiment || !canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    await studentUiFeedbackAPI.create({
      sentiment: form.sentiment,
      category: form.category,
      message: form.message.trim() || undefined,
      pagePath: route.path,
      uiVersion: 'v2',
      viewportWidth: window.innerWidth,
      platform: platformName(),
      appVersion: import.meta.env.VITE_APP_VERSION || packageMetadata.version,
    })
    visible.value = false
    ElMessage.success('의견을 보내주셔서 고마워요!')
  } catch (error) {
    const message = axios.isAxiosError(error)
      ? error.response?.data?.message || '의견을 보내지 못했습니다'
      : '의견을 보내지 못했습니다'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    class="student-feedback-dialog"
    modal-class="student-feedback-overlay"
    width="520px"
    :show-close="false"
    append-to-body
    destroy-on-close
    @closed="resetForm"
  >
    <template #header>
      <div class="student-feedback-dialog__header">
        <div>
          <p>NEW UI FEEDBACK</p>
          <h2>새 화면은 어떠셨나요?</h2>
        </div>
        <button type="button" aria-label="의견 보내기 닫기" @click="visible = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <p class="student-feedback-dialog__description">
      짧은 의견도 좋아요. 더 편한 학생 화면을 만드는 데 반영할게요.
    </p>

    <fieldset class="student-feedback-fieldset">
      <legend>전반적으로 어땠나요? <strong>필수</strong></legend>
      <div class="student-feedback-sentiments">
        <button
          v-for="item in sentiments"
          :key="item.value"
          type="button"
          :class="{ 'is-selected': form.sentiment === item.value }"
          :aria-pressed="form.sentiment === item.value"
          @click="form.sentiment = item.value"
        >
          <span aria-hidden="true">{{ item.emoji }}</span>
          {{ item.label }}
        </button>
      </div>
    </fieldset>

    <fieldset v-if="needsCategory" class="student-feedback-fieldset">
      <legend>어떤 점이 불편했나요? <strong>필수</strong></legend>
      <div class="student-feedback-categories">
        <button
          v-for="item in categories"
          :key="item.value"
          type="button"
          :class="{ 'is-selected': form.category === item.value }"
          :aria-pressed="form.category === item.value"
          @click="form.category = item.value"
        >
          {{ item.label }}
        </button>
      </div>
    </fieldset>

    <label class="student-feedback-message">
      <span>더 알려주고 싶은 내용 <small>선택</small></span>
      <el-input
        v-model="form.message"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        resize="none"
        placeholder="어느 화면에서 무엇이 불편했는지 알려주세요."
      />
    </label>

    <p class="student-feedback-dialog__meta">
      현재 화면과 기기 정보가 함께 전송돼요. PIN 같은 개인정보는 적지 마세요.
    </p>

    <template #footer>
      <div class="student-feedback-dialog__footer">
        <el-button @click="visible = false">다음에 할게요</el-button>
        <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">
          의견 보내기
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style>
.student-feedback-overlay {
  background: var(--student-overlay);
  backdrop-filter: blur(3px);
}
.student-feedback-overlay .el-overlay-dialog {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.student-feedback-dialog.el-dialog {
  max-width: calc(100vw - 32px);
  max-height: calc(100dvh - 40px);
  margin: 0;
  overflow: hidden;
  border-radius: calc(var(--student-radius-lg) + var(--student-space-1));
  background: var(--student-surface);
  box-shadow: var(--student-shadow-dialog);
}
.student-feedback-dialog .el-dialog__header {
  padding: 22px 22px 0;
}
.student-feedback-dialog .el-dialog__body {
  max-height: calc(100dvh - 190px);
  padding: 14px 22px 18px;
  overflow-y: auto;
}
.student-feedback-dialog .el-dialog__footer {
  padding: 0 22px 22px;
}
.student-feedback-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.student-feedback-dialog__header p {
  margin: 0 0 4px;
  color: var(--student-primary);
  font-size: var(--student-font-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
}
.student-feedback-dialog__header h2 {
  margin: 0;
  color: var(--student-ink);
  font-size: var(--student-font-title-md);
  font-weight: 850;
  letter-spacing: -0.035em;
}
.student-feedback-dialog__header button {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--student-border);
  border-radius: 13px;
  color: var(--student-text);
  background: var(--student-surface);
  cursor: pointer;
}
.student-feedback-dialog__description {
  margin: 0 0 20px;
  color: var(--student-muted);
  font-size: var(--student-font-md);
  line-height: var(--student-leading-body);
}
.student-feedback-fieldset {
  min-width: 0;
  margin: 0 0 20px;
  padding: 0;
  border: 0;
}
.student-feedback-fieldset legend,
.student-feedback-message > span {
  display: block;
  margin-bottom: 10px;
  color: var(--student-text);
  font-size: var(--student-font-md);
  font-weight: 800;
}
.student-feedback-fieldset legend strong {
  margin-left: 3px;
  color: var(--student-primary);
  font-size: 10px;
}
.student-feedback-sentiments {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.student-feedback-sentiments button {
  display: grid;
  min-width: 0;
  min-height: 72px;
  place-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: 1px solid var(--student-border);
  border-radius: 15px;
  color: var(--student-text);
  background: var(--student-surface);
  font: inherit;
  font-size: var(--student-font-sm);
  font-weight: 750;
  cursor: pointer;
}
.student-feedback-sentiments button span {
  font-size: 22px;
}
.student-feedback-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.student-feedback-categories button {
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-pill);
  color: var(--student-text);
  background: var(--student-surface);
  font: inherit;
  font-size: var(--student-font-sm);
  font-weight: 700;
  cursor: pointer;
}
.student-feedback-sentiments button.is-selected,
.student-feedback-categories button.is-selected {
  border-color: var(--student-primary);
  color: var(--student-primary-strong);
  background: var(--student-primary-soft);
  box-shadow: 0 0 0 2px rgba(36, 87, 214, 0.1);
}
.student-feedback-sentiments button:focus-visible,
.student-feedback-categories button:focus-visible,
.student-feedback-dialog__header button:focus-visible {
  outline: 0;
  box-shadow: var(--student-focus-ring);
}
.student-feedback-message {
  display: block;
}
.student-feedback-message > span small {
  color: var(--student-text-disabled);
  font-size: 10px;
}
.student-feedback-message .el-textarea__inner {
  border-radius: var(--student-radius-md);
  box-shadow: 0 0 0 1px var(--student-border) inset;
  font-family: inherit;
}
.student-feedback-dialog__meta {
  margin: 11px 0 0;
  color: var(--student-slate-500);
  font-size: var(--student-font-xs);
  line-height: 1.5;
}
.student-feedback-dialog__footer {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 10px;
}
.student-feedback-dialog__footer .el-button {
  min-height: var(--student-control-height-md);
  margin: 0;
  border-radius: var(--student-radius-md);
  font-weight: 800;
}
@media (max-width: 600px) {
  .student-feedback-overlay .el-overlay-dialog {
    align-items: flex-end;
    padding: 0;
  }
  .student-feedback-dialog.el-dialog {
    width: 100% !important;
    max-width: none;
    max-height: calc(100dvh - env(safe-area-inset-top));
    border-radius: calc(var(--student-radius-lg) + var(--student-space-1))
      calc(var(--student-radius-lg) + var(--student-space-1)) 0 0;
  }
  .student-feedback-dialog .el-dialog__header {
    padding: 20px 18px 0;
  }
  .student-feedback-dialog .el-dialog__body {
    max-height: calc(100dvh - 184px - env(safe-area-inset-top));
    padding: 12px 18px 16px;
  }
  .student-feedback-dialog .el-dialog__footer {
    padding: 0 18px calc(16px + env(safe-area-inset-bottom));
  }
  .student-feedback-dialog__header h2 {
    font-size: 21px;
  }
}
@media (max-width: 350px) {
  .student-feedback-sentiments button {
    min-height: 66px;
    font-size: 11px;
  }
  .student-feedback-dialog__footer {
    grid-template-columns: 1fr;
  }
  .student-feedback-dialog__footer .el-button:first-child {
    display: none;
  }
}
</style>
