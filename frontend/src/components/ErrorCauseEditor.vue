<script setup lang="ts">
import { ref, watch } from 'vue'
import { submissionAPI, type ErrorCauseCounts, type ErrorCauses } from '@/api/client'
import { ERROR_CAUSES, type ErrorCauseKey } from '@/utils/errorCauses'

const props = defineProps<{ submissionId: number; initialValue?: ErrorCauses | null }>()
const emit = defineEmits<{ saved: [value: ErrorCauses]; cancel: [] }>()
const draft = ref({} as Record<ErrorCauseKey, string>)
const saving = ref(false)
const error = ref('')

watch(
  () => [props.submissionId, props.initialValue],
  () => {
    for (const cause of ERROR_CAUSES) {
      draft.value[cause.key] = props.initialValue?.[cause.key]?.toString() ?? ''
    }
    error.value = ''
  },
  { immediate: true },
)

async function save() {
  if (saving.value) return
  error.value = ''
  const counts = {} as ErrorCauseCounts
  for (const cause of ERROR_CAUSES) {
    const text = draft.value[cause.key].trim()
    const count = text === '' ? null : Number(text)
    if (count !== null && (!/^\d+$/.test(text) || !Number.isInteger(count) || count > 2147483647)) {
      error.value = `${cause.label}: 0 이상의 정수를 입력해 주세요.`
      return
    }
    counts[cause.key] = count
  }
  saving.value = true
  try {
    const response = await submissionAPI.saveErrorCauses(props.submissionId, counts)
    emit('saved', response.data)
  } catch {
    error.value = '저장하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="cause-editor" @submit.prevent="save">
    <p class="cause-editor__note">
      빈칸은 미입력, 0은 해당 원인이 없다는 뜻입니다. 한 문항에 여러 원인을 기록할 수 있습니다.
    </p>
    <p v-if="initialValue?.status === 'NEEDS_REVIEW'" class="cause-editor__review" role="status">
      답안 또는 채점이 변경되었습니다. 기존 원인을 검토한 뒤 저장해 주세요.
    </p>
    <fieldset :disabled="saving">
      <legend class="cause-editor__legend">오답 원인별 기록 건수</legend>
      <label v-for="cause in ERROR_CAUSES" :key="cause.key" class="cause-editor__field">
        <span>{{ cause.label }}</span>
        <span class="cause-editor__input">
          <input
            v-model="draft[cause.key]"
            type="text"
            inputmode="numeric"
            :aria-label="`${cause.label} 건수`"
            placeholder="미입력"
            autocomplete="off"
          />
          <span>건</span>
        </span>
      </label>
    </fieldset>
    <p v-if="error" role="alert" class="cause-editor__error">{{ error }}</p>
    <div class="cause-editor__actions">
      <button type="button" :disabled="saving" @click="emit('cancel')">취소</button>
      <button type="submit" :disabled="saving">{{ saving ? '저장 중…' : '저장' }}</button>
    </div>
  </form>
</template>

<style scoped>
.cause-editor {
  color: #303133;
}
.cause-editor__note,
.cause-editor__review {
  margin: 0 0 16px;
  line-height: 1.6;
  font-size: 13px;
}
.cause-editor__note {
  color: #606266;
}
.cause-editor__review {
  padding: 12px;
  background: #fff7ed;
  color: #9a3412;
  border-radius: 8px;
}
fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}
.cause-editor__legend {
  margin-bottom: 12px;
  font-weight: 600;
}
.cause-editor__field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.cause-editor__input {
  display: flex;
  align-items: center;
  gap: 8px;
}
input {
  width: 100px;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid #c0c4cc;
  border-radius: 6px;
  font: inherit;
  text-align: right;
  box-sizing: border-box;
}
input:focus-visible,
button:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 3px;
}
.cause-editor__error {
  color: #b91c1c;
  font-size: 13px;
}
.cause-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
button {
  min-width: 72px;
  min-height: 44px;
  padding: 8px 16px;
  border: 1px solid #c0c4cc;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  cursor: pointer;
}
button[type='submit'] {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
button:disabled {
  opacity: 0.6;
  cursor: wait;
}
</style>
