<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  textbookAPI,
  type Textbook,
  type TextbookProblem,
  type TextbookQuestionType,
} from '@/api/client'
import TextbookProblemsBulkImportDialog from '@/components/TextbookProblemsBulkImportDialog.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const textbook = ref<Textbook | null>(null)
const problems = ref<TextbookProblem[]>([])

const textbookId = computed(() => Number(route.params.id))

const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const draft = ref<TextbookProblem>({
  number: 1,
  questionType: 'OBJECTIVE',
  answer: '',
  topic: '',
  videoLink: '',
})

const typeOptions: { label: string; value: TextbookQuestionType }[] = [
  { label: '객관식', value: 'OBJECTIVE' },
  { label: '주관식', value: 'SUBJECTIVE' },
  { label: '서술형', value: 'ESSAY' },
]

const typeLabel = (v?: string) => typeOptions.find(o => o.value === v)?.label ?? v ?? ''

const fetchAll = async () => {
  loading.value = true
  try {
    const tbRes = await textbookAPI.get(textbookId.value)
    textbook.value = tbRes.data
    problems.value = tbRes.data.problems ?? []
  } catch {
    ElMessage.error('교재를 불러오지 못했습니다')
    router.push('/textbooks')
  } finally {
    loading.value = false
  }
}

const openAdd = () => {
  editMode.value = false
  editingId.value = null
  const nextNum = problems.value.length > 0
    ? Math.max(...problems.value.map(p => p.number)) + 1
    : 1
  draft.value = {
    number: nextNum,
    questionType: 'OBJECTIVE',
    answer: '',
    topic: '',
    videoLink: '',
  }
  dialogVisible.value = true
}

const openEdit = (p: TextbookProblem) => {
  editMode.value = true
  editingId.value = p.id ?? null
  draft.value = {
    number: p.number,
    questionType: p.questionType,
    answer: p.answer ?? '',
    topic: p.topic ?? '',
    videoLink: p.videoLink ?? '',
  }
  dialogVisible.value = true
}

const save = async () => {
  if (!draft.value.number || draft.value.number < 1) {
    ElMessage.warning('문제 번호는 1 이상이어야 합니다')
    return
  }
  try {
    if (editMode.value && editingId.value) {
      await textbookAPI.updateProblem(editingId.value, draft.value)
      ElMessage.success('수정되었습니다')
    } else {
      await textbookAPI.createProblem(textbookId.value, draft.value)
      ElMessage.success('추가되었습니다')
    }
    dialogVisible.value = false
    await fetchAll()
  } catch {
    ElMessage.error('저장에 실패했습니다')
  }
}

const remove = async (p: TextbookProblem) => {
  try {
    await ElMessageBox.confirm(
      `${p.number}번 문제를 삭제할까요?`,
      '문제 삭제',
      { type: 'warning', confirmButtonText: '삭제', cancelButtonText: '취소' },
    )
  } catch {
    return
  }
  try {
    await textbookAPI.deleteProblem(p.id!)
    ElMessage.success('삭제되었습니다')
    await fetchAll()
  } catch {
    ElMessage.error('삭제에 실패했습니다')
  }
}

const editTitle = async () => {
  if (!textbook.value) return
  try {
    const { value } = await ElMessageBox.prompt('새 제목', '교재 제목 수정', {
      inputValue: textbook.value.title,
      confirmButtonText: '저장',
      cancelButtonText: '취소',
      inputValidator: v => (v && v.trim()) ? true : '제목을 입력하세요',
    })
    await textbookAPI.update(textbookId.value, value.trim())
    await fetchAll()
    ElMessage.success('수정되었습니다')
  } catch {
    /* cancelled or failed */
  }
}

const bulkVisible = ref(false)
const existingNumbers = computed(() => problems.value.map(p => p.number))

onMounted(fetchAll)
</script>

<template>
  <div v-loading="loading" style="padding: 24px; max-width: 1200px; margin: 0 auto">
    <el-card shadow="never" style="margin-bottom: 16px">
      <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
        <div style="display: flex; align-items: center; gap: 12px">
          <el-button @click="router.push('/textbooks')">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <h1 style="margin: 0; font-size: 22px; font-weight: 600">
            {{ textbook?.title || '...' }}
          </h1>
          <el-button text @click="editTitle">
            <el-icon><Edit /></el-icon>
          </el-button>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button @click="bulkVisible = true">
            <el-icon style="margin-right: 4px"><DocumentCopy /></el-icon>
            여러 문제 추가
          </el-button>
          <el-button type="primary" @click="openAdd">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>
            문제 추가
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-empty v-if="problems.length === 0" description="문제가 없습니다" />
      <el-table v-else :data="problems" stripe>
        <el-table-column prop="number" label="번호" width="80" align="center" />
        <el-table-column prop="questionType" label="형식" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.questionType === 'OBJECTIVE' ? 'primary' : row.questionType === 'SUBJECTIVE' ? 'success' : 'warning'">
              {{ typeLabel(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="topic" label="유형(주제)" min-width="160">
          <template #default="{ row }">
            <span v-if="row.topic" style="color: #409eff">{{ row.topic }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="answer" label="답" width="120">
          <template #default="{ row }">
            <span v-if="row.answer">{{ row.answer }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="해설 영상" min-width="180">
          <template #default="{ row }">
            <a v-if="row.videoLink" :href="row.videoLink" target="_blank" rel="noopener" style="color: #409eff">
              ▶ 보기
            </a>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">수정</el-button>
            <el-button size="small" type="danger" @click="remove(row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editMode ? '문제 수정' : '문제 추가'" width="520px">
      <el-form label-position="top">
        <el-form-item label="문제 번호">
          <el-input-number v-model="draft.number" :min="1" />
        </el-form-item>
        <el-form-item label="출제 형식">
          <el-select v-model="draft.questionType" style="width: 100%">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="유형/주제 (선택)">
          <el-input v-model="draft.topic" placeholder="예: 일차함수" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="답 (선택, 시험 출제 시 사용)">
          <el-input v-model="draft.answer" placeholder="예: 3" maxlength="100" />
        </el-form-item>
        <el-form-item label="해설 영상 링크 (선택)">
          <el-input v-model="draft.videoLink" placeholder="https://..." maxlength="1024" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" @click="save">저장</el-button>
      </template>
    </el-dialog>

    <TextbookProblemsBulkImportDialog
      v-model:visible="bulkVisible"
      :textbook-id="textbookId"
      :existing-numbers="existingNumbers"
      @imported="fetchAll"
    />
  </div>
</template>
