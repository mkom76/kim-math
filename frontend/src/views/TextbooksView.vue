<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { textbookAPI, type Textbook } from '@/api/client'

const router = useRouter()
const loading = ref(false)
const textbooks = ref<Textbook[]>([])

const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const titleInput = ref('')

const fetchTextbooks = async () => {
  loading.value = true
  try {
    const res = await textbookAPI.list()
    textbooks.value = res.data
  } catch {
    ElMessage.error('교재 목록을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

const openAdd = () => {
  editMode.value = false
  editingId.value = null
  titleInput.value = ''
  dialogVisible.value = true
}

const openEdit = (tb: Textbook) => {
  editMode.value = true
  editingId.value = tb.id ?? null
  titleInput.value = tb.title
  dialogVisible.value = true
}

const save = async () => {
  const title = titleInput.value.trim()
  if (!title) {
    ElMessage.warning('제목을 입력하세요')
    return
  }
  try {
    if (editMode.value && editingId.value) {
      await textbookAPI.update(editingId.value, title)
      ElMessage.success('교재가 수정되었습니다')
    } else {
      await textbookAPI.create(title)
      ElMessage.success('교재가 생성되었습니다')
    }
    dialogVisible.value = false
    await fetchTextbooks()
  } catch {
    ElMessage.error('저장에 실패했습니다')
  }
}

const remove = async (tb: Textbook) => {
  try {
    await ElMessageBox.confirm(
      `"${tb.title}" 교재를 삭제할까요? 안에 있는 모든 문제가 함께 삭제됩니다.`,
      '교재 삭제',
      { type: 'warning', confirmButtonText: '삭제', cancelButtonText: '취소' },
    )
  } catch {
    return
  }
  try {
    await textbookAPI.remove(tb.id!)
    ElMessage.success('삭제되었습니다')
    await fetchTextbooks()
  } catch {
    ElMessage.error('삭제에 실패했습니다')
  }
}

const goDetail = (tb: Textbook) => {
  if (tb.id) router.push(`/textbooks/${tb.id}`)
}

onMounted(fetchTextbooks)
</script>

<template>
  <div v-loading="loading" style="padding: 24px; max-width: 1200px; margin: 0 auto">
    <el-card shadow="never" style="margin-bottom: 16px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <h1 style="margin: 0; font-size: 24px; font-weight: 600">교재 관리</h1>
        <el-button type="primary" @click="openAdd">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          새 교재
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-empty v-if="textbooks.length === 0" description="등록된 교재가 없습니다" />
      <el-table v-else :data="textbooks" stripe @row-click="goDetail" style="cursor: pointer">
        <el-table-column prop="title" label="제목" min-width="240" />
        <el-table-column prop="problemCount" label="문제 수" width="120" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.problemCount ?? 0 }} 문제</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" align="center">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openEdit(row)">제목 수정</el-button>
            <el-button size="small" type="danger" @click.stop="remove(row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editMode ? '교재 수정' : '새 교재'" width="420px">
      <el-form label-position="top">
        <el-form-item label="제목">
          <el-input v-model="titleInput" placeholder="예: 쎈 수학 상" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" @click="save">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>
