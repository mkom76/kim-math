<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  homeworkAPI,
  type HomeworkProblemRow,
  type TextbookProblem,
} from '@/api/client'
import TextbookProblemPicker from './TextbookProblemPicker.vue'

const props = defineProps<{
  visible: boolean
  homeworkId: number
  homeworkTitle?: string
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

interface Row {
  // 클라이언트 식별자 (서버 id가 없는 새 row 구분)
  key: string
  textbookProblem: { id: number; topic?: string | null; videoLink?: string | null; number: number } | null
}

const rows = ref<Row[]>([])
const loading = ref(false)
const pickerVisible = ref(false)

let nextKey = 0
const newKey = () => `r${++nextKey}`

const usedTextbookIds = computed(() =>
  rows.value.map(r => r.textbookProblem?.id).filter((x): x is number => x != null),
)

const fetch = async () => {
  loading.value = true
  try {
    const res = await homeworkAPI.getProblems(props.homeworkId)
    const list = (res.data as HomeworkProblemRow[]) ?? []
    rows.value = list.map(p => ({
      key: newKey(),
      textbookProblem: p.textbookProblem
        ? { id: p.textbookProblem.id, topic: p.textbookProblem.topic, videoLink: p.textbookProblem.videoLink, number: p.textbookProblem.number }
        : null,
    }))
  } catch {
    ElMessage.error('문제 구성을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, v => {
  if (v) fetch()
})

const addManual = () => {
  rows.value.push({ key: newKey(), textbookProblem: null })
}

const onPick = (items: { problem: TextbookProblem }[]) => {
  for (const it of items) {
    rows.value.push({
      key: newKey(),
      textbookProblem: {
        id: it.problem.id!,
        topic: it.problem.topic,
        videoLink: it.problem.videoLink,
        number: it.problem.number,
      },
    })
  }
}

const removeRow = (idx: number) => {
  rows.value.splice(idx, 1)
}

const moveUp = (idx: number) => {
  if (idx === 0) return
  const tmp = rows.value[idx - 1]!
  rows.value[idx - 1] = rows.value[idx]!
  rows.value[idx] = tmp
}

const moveDown = (idx: number) => {
  if (idx === rows.value.length - 1) return
  const tmp = rows.value[idx + 1]!
  rows.value[idx + 1] = rows.value[idx]!
  rows.value[idx] = tmp
}

const save = async () => {
  loading.value = true
  try {
    const payload = rows.value.map(r => ({
      textbookProblemId: r.textbookProblem?.id ?? null,
    }))
    await homeworkAPI.replaceProblems(props.homeworkId, payload)
    ElMessage.success('문제 구성이 저장되었습니다')
    emit('saved')
    emit('update:visible', false)
  } catch {
    ElMessage.error('저장에 실패했습니다')
  } finally {
    loading.value = false
  }
}

const close = () => emit('update:visible', false)
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    :title="`문제 구성: ${homeworkTitle || ''}`"
    width="720px"
  >
    <div v-loading="loading">
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-button @click="addManual">
          <el-icon><Plus /></el-icon>
          수동 슬롯 추가
        </el-button>
        <el-button type="primary" plain @click="pickerVisible = true">
          <el-icon><Collection /></el-icon>
          교재에서 문제 추가
        </el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px; align-self: center">
          총 {{ rows.length }} 문제
        </span>
      </div>

      <div style="max-height: 460px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 6px">
        <el-empty v-if="rows.length === 0" description="문제를 추가하세요" />
        <el-table v-else :data="rows.map((r, i) => ({ ...r, position: i + 1 }))" row-key="key">
          <el-table-column prop="position" label="번호" width="60" align="center" />
          <el-table-column label="유형">
            <template #default="{ row, $index }">
              <el-tag v-if="!row.textbookProblem" type="info" size="small">수동 슬롯</el-tag>
              <div v-else style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
                <el-tag type="success" size="small">📚 #{{ row.textbookProblem.number }}</el-tag>
                <span v-if="row.textbookProblem.topic" style="color: #409eff; font-size: 13px">
                  {{ row.textbookProblem.topic }}
                </span>
                <a v-if="row.textbookProblem.videoLink" :href="row.textbookProblem.videoLink" target="_blank" rel="noopener" style="color: #409eff; font-size: 13px">
                  ▶ 해설
                </a>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="순서" width="120" align="center">
            <template #default="{ $index }">
              <el-button size="small" :disabled="$index === 0" @click="moveUp($index)">↑</el-button>
              <el-button size="small" :disabled="$index === rows.length - 1" @click="moveDown($index)">↓</el-button>
            </template>
          </el-table-column>
          <el-table-column label="" width="60" align="center">
            <template #default="{ $index }">
              <el-button size="small" type="danger" plain @click="removeRow($index)">×</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">취소</el-button>
      <el-button type="primary" @click="save" :loading="loading">저장</el-button>
    </template>

    <TextbookProblemPicker
      v-model:visible="pickerVisible"
      :exclude-problem-ids="usedTextbookIds"
      @pick="onPick"
    />
  </el-dialog>
</template>
