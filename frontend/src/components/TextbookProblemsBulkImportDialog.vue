<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { textbookAPI, type TextbookProblem } from '@/api/client'
import { parseTextbookProblems, type ParsedRow } from './textbookProblemsParser'

const props = defineProps<{
  visible: boolean
  textbookId: number
  /** 이미 교재에 있는 번호들 (중복 경고용) */
  existingNumbers: number[]
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'imported'): void
}>()

const inputText = ref('')
const saving = ref(false)

const rows = computed<ParsedRow[]>(() => parseTextbookProblems(inputText.value))

const validCount = computed(() => rows.value.filter(r => r.ok).length)
const errorCount = computed(() => rows.value.filter(r => !r.ok).length)
const dupSet = computed(() => new Set(props.existingNumbers))
const dupRows = computed(() =>
  rows.value.filter(r => r.ok && r.data?.number != null && dupSet.value.has(r.data.number)),
)

watch(() => props.visible, v => {
  if (!v) inputText.value = ''
})

const close = () => emit('update:visible', false)

const submit = async () => {
  const okRows = rows.value.filter(r => r.ok && r.data)
  if (okRows.length === 0) {
    ElMessage.warning('추가할 유효한 행이 없습니다')
    return
  }
  saving.value = true
  try {
    const payload: Array<Partial<TextbookProblem>> = okRows.map(r => ({
      number: r.data!.number!,
      questionType: r.data!.questionType,
      topic: r.data!.topic,
      answer: r.data!.answer,
      videoLink: r.data!.videoLink,
    }))
    await textbookAPI.bulkCreateProblems(props.textbookId, payload)
    ElMessage.success(`${okRows.length}개 문제를 추가했습니다`)
    emit('imported')
    close()
  } catch {
    ElMessage.error('일괄 추가에 실패했습니다')
  } finally {
    saving.value = false
  }
}

const typeLabel = (t?: string | null) =>
  ({ OBJECTIVE: '객관식', SUBJECTIVE: '주관식', ESSAY: '서술형' } as any)[t ?? ''] ?? '—'
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="여러 문제 한꺼번에 추가"
    width="820px"
    top="5vh"
  >
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      <template #title>
        <strong>붙여넣기 가이드</strong>
      </template>
      <div style="font-size: 13px; line-height: 1.7">
        <div>엑셀에서 영역을 복사한 다음 아래 칸에 붙여넣으세요. 컬럼 순서는 다음과 같이 고정입니다:</div>
        <div style="margin-top: 4px; font-family: monospace; background: #f5f7fa; padding: 6px 10px; border-radius: 4px">
          번호 │ 형식 │ 주제 │ 답 │ 해설영상 URL
        </div>
        <div style="margin-top: 6px">
          - 형식은 <code>객관식</code> / <code>주관식</code> / <code>서술형</code> (또는 OBJECTIVE/SUBJECTIVE/ESSAY)
          <br />- 빈 칸은 그대로 비워두세요. 번호만 필수입니다.
        </div>
      </div>
    </el-alert>

    <el-input
      v-model="inputText"
      type="textarea"
      :rows="8"
      :placeholder="'예시:\n1\t객관식\t일차함수\t3\thttps://youtu.be/abc\n2\t주관식\t이차방정식\tx=2\n3\t\t도형의 닮음'"
      style="font-family: monospace"
    />

    <div v-if="rows.length > 0" style="margin-top: 16px">
      <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 8px">
        <el-tag type="success" size="small">유효 {{ validCount }}</el-tag>
        <el-tag v-if="errorCount > 0" type="danger" size="small">오류 {{ errorCount }}</el-tag>
        <el-tag v-if="dupRows.length > 0" type="warning" size="small">중복 번호 {{ dupRows.length }}</el-tag>
        <span style="margin-left: auto; color: #909399; font-size: 12px">
          중복 번호는 추가됩니다 (필요 시 수동 정리)
        </span>
      </div>

      <el-table :data="rows" :max-height="320" :row-class-name="r => !r.row.ok ? 'row-error' : (r.row.data?.number != null && dupSet.has(r.row.data.number) ? 'row-warn' : '')">
        <el-table-column type="index" label="줄" width="50" align="center" />
        <el-table-column label="상태" width="60" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.ok" color="#67c23a"><CircleCheck /></el-icon>
            <el-icon v-else color="#f56c6c"><CircleClose /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="번호" width="60" align="center">
          <template #default="{ row }">{{ row.data?.number ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="형식" width="80">
          <template #default="{ row }">{{ typeLabel(row.data?.questionType) }}</template>
        </el-table-column>
        <el-table-column label="주제" min-width="120">
          <template #default="{ row }">{{ row.data?.topic ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="답" width="100">
          <template #default="{ row }">{{ row.data?.answer ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="해설" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.data?.videoLink" style="color: #409eff">▶</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="에러" min-width="200">
          <template #default="{ row }">
            <span v-if="row.errors.length > 0" style="color: #f56c6c; font-size: 12px">
              {{ row.errors.join(' / ') }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="close">취소</el-button>
      <el-button
        type="primary"
        :disabled="validCount === 0"
        :loading="saving"
        @click="submit"
      >
        {{ validCount }}개 추가{{ errorCount > 0 ? ` (오류 ${errorCount}개 제외)` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.row-error) {
  background-color: #fef0f0 !important;
}
:deep(.row-warn) {
  background-color: #fdf6ec !important;
}
</style>
