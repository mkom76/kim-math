<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { textbookAPI, type Textbook, type TextbookProblem } from '@/api/client'

const props = defineProps<{
  visible: boolean
  /** 이미 시험에 들어간 문제 ID들 (재선택 방지). null이면 제한 없음 */
  excludeProblemIds?: number[]
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'pick', items: { problem: TextbookProblem }[]): void
}>()

const textbooks = ref<Textbook[]>([])
const selectedTextbookId = ref<number | null>(null)
const problems = ref<TextbookProblem[]>([])
const selectedIds = ref<Set<number>>(new Set())
const topicFilter = ref('')
const typeFilter = ref<'' | 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY'>('')
const loadingTextbooks = ref(false)
const loadingProblems = ref(false)

const filteredProblems = computed(() => {
  let list = problems.value
  if (topicFilter.value) {
    const q = topicFilter.value.toLowerCase()
    list = list.filter(p => (p.topic || '').toLowerCase().includes(q))
  }
  if (typeFilter.value) {
    list = list.filter(p => p.questionType === typeFilter.value)
  }
  return list
})

const isExcluded = (p: TextbookProblem) =>
  !!props.excludeProblemIds?.includes(p.id ?? -1)

const fetchTextbooks = async () => {
  loadingTextbooks.value = true
  try {
    const res = await textbookAPI.list()
    textbooks.value = res.data
    if (textbooks.value.length > 0 && !selectedTextbookId.value) {
      selectedTextbookId.value = textbooks.value[0]!.id ?? null
    }
  } catch {
    ElMessage.error('교재 목록을 불러오지 못했습니다')
  } finally {
    loadingTextbooks.value = false
  }
}

const fetchProblems = async () => {
  if (!selectedTextbookId.value) {
    problems.value = []
    return
  }
  loadingProblems.value = true
  try {
    const res = await textbookAPI.listProblems(selectedTextbookId.value)
    problems.value = res.data
    selectedIds.value = new Set()
  } catch {
    ElMessage.error('문제 목록을 불러오지 못했습니다')
  } finally {
    loadingProblems.value = false
  }
}

watch(() => props.visible, v => {
  if (v) {
    fetchTextbooks().then(() => fetchProblems())
  }
})

watch(selectedTextbookId, () => {
  fetchProblems()
})

const toggleProblem = (id?: number) => {
  if (id == null) return
  const set = new Set(selectedIds.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedIds.value = set
}

const close = () => emit('update:visible', false)

const confirm = () => {
  const picked = problems.value
    .filter(p => p.id != null && selectedIds.value.has(p.id))
    .map(problem => ({ problem }))
  if (picked.length === 0) {
    ElMessage.warning('하나 이상의 문제를 선택하세요')
    return
  }
  emit('pick', picked)
  close()
}

const typeLabel = (t?: string) => ({ OBJECTIVE: '객관식', SUBJECTIVE: '주관식', ESSAY: '서술형' } as any)[t ?? ''] ?? ''
</script>

<template>
  <el-dialog :model-value="visible" @update:model-value="close" title="교재에서 문제 가져오기" width="720px">
    <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center; margin-bottom: 12px">
      <el-select v-model="selectedTextbookId" placeholder="교재 선택" style="width: 220px" :loading="loadingTextbooks">
        <el-option v-for="tb in textbooks" :key="tb.id" :label="tb.title" :value="tb.id" />
      </el-select>
      <el-input v-model="topicFilter" placeholder="유형 필터" style="width: 180px" clearable />
      <el-select v-model="typeFilter" placeholder="형식 필터" style="width: 140px" clearable>
        <el-option label="객관식" value="OBJECTIVE" />
        <el-option label="주관식" value="SUBJECTIVE" />
        <el-option label="서술형" value="ESSAY" />
      </el-select>
      <span style="margin-left: auto; color: #909399; font-size: 13px">
        선택: {{ selectedIds.size }}개
      </span>
    </div>

    <div v-loading="loadingProblems" style="max-height: 420px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 6px">
      <el-empty v-if="filteredProblems.length === 0" description="해당하는 문제가 없습니다" />
      <el-table v-else :data="filteredProblems">
        <el-table-column width="50">
          <template #default="{ row }">
            <el-checkbox
              :model-value="row.id != null && selectedIds.has(row.id)"
              :disabled="isExcluded(row)"
              @change="toggleProblem(row.id)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="number" label="번호" width="70" align="center" />
        <el-table-column label="형식" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="topic" label="유형" />
        <el-table-column prop="answer" label="답" width="100">
          <template #default="{ row }">
            <span v-if="row.answer">{{ row.answer }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="해설" width="80" align="center">
          <template #default="{ row }">
            <a v-if="row.videoLink" :href="row.videoLink" target="_blank" rel="noopener" @click.stop>▶</a>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="close">취소</el-button>
      <el-button type="primary" @click="confirm">{{ selectedIds.size }}개 추가</el-button>
    </template>
  </el-dialog>
</template>
