<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { homeworkAPI, academyAPI, academyClassAPI, type Homework, type Academy, type AcademyClass } from '../api/client'
import { usePagination } from '../composables/usePagination'
import { useAuthStore } from '@/stores/auth'
import HomeworkProblemsComposer from '@/components/HomeworkProblemsComposer.vue'

const composerVisible = ref(false)
const composerHomeworkId = ref<number | null>(null)
const composerHomeworkTitle = ref<string>('')

const openComposer = (hw: Homework) => {
  if (!hw.id) return
  composerHomeworkId.value = hw.id
  composerHomeworkTitle.value = hw.title || ''
  composerVisible.value = true
}

const authStore = useAuthStore()
const loading = ref(false)
const homeworks = ref<Homework[]>([])
const academies = ref<Academy[]>([])
const allClasses = ref<AcademyClass[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const editMode = ref(false)
const currentHomework = ref<Homework>({ title: '', questionCount: 10, academyId: undefined, classId: undefined })

const { currentPage, pageSize } = usePagination('homeworks-view')

const availableClasses = computed(() => {
  if (!currentHomework.value.academyId) return []
  return allClasses.value.filter(cls => cls.academyId === currentHomework.value.academyId)
})

const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}

const isOverdue = (dateString: string) => {
  const dueDate = new Date(dateString)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return dueDate < today
}

const isDueSoon = (dateString: string) => {
  const dueDate = new Date(dateString)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const threeDaysLater = new Date(today)
  threeDaysLater.setDate(today.getDate() + 3)
  return dueDate >= today && dueDate <= threeDaysLater
}

const filteredData = computed(() => {
  if (!searchQuery.value) return homeworks.value
  return homeworks.value.filter(homework =>
    (homework.title || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (homework.academyName || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (homework.className || '').toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

const totalItems = computed(() => filteredData.value.length)

watch(() => currentHomework.value.academyId, () => {
  currentHomework.value.classId = undefined
})

const fetchHomeworks = async () => {
  loading.value = true
  try {
    const response = await homeworkAPI.getHomeworks({ size: 10000 })
    homeworks.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('숙제 목록을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const fetchAcademies = async () => {
  try {
    const response = await academyAPI.getAcademies({ size: 10000 })
    academies.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('학원 목록을 불러오는데 실패했습니다.')
  }
}

const fetchClasses = async () => {
  try {
    const response = await academyClassAPI.getAcademyClasses({ size: 10000 })
    allClasses.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('반 목록을 불러오는데 실패했습니다.')
  }
}

const openAddDialog = () => {
  editMode.value = false
  currentHomework.value = {
    title: '',
    questionCount: 10,
    academyId: authStore.activeAcademyId ?? undefined,
    classId: undefined
  }
  dialogVisible.value = true
}

const openEditDialog = (homework: Homework) => {
  editMode.value = true
  currentHomework.value = { ...homework }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!currentHomework.value.academyId || !currentHomework.value.classId) {
    ElMessage.error('학원과 반을 선택해주세요.')
    return
  }

  try {
    if (editMode.value && currentHomework.value.id) {
      await homeworkAPI.updateHomework(currentHomework.value.id, currentHomework.value)
      ElMessage.success('숙제 정보가 수정되었습니다.')
    } else {
      await homeworkAPI.createHomework(currentHomework.value)
      ElMessage.success('숙제가 추가되었습니다.')
    }
    dialogVisible.value = false
    fetchHomeworks()
  } catch (error) {
    ElMessage.error('작업을 완료할 수 없습니다.')
  }
}

const handleDelete = async (homework: Homework) => {
  if (!homework.id) return

  try {
    await ElMessageBox.confirm(
      `${homework.title} 숙제를 삭제하시겠습니까?`,
      '삭제 확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await homeworkAPI.deleteHomework(homework.id)
    ElMessage.success('숙제가 삭제되었습니다.')
    fetchHomeworks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('삭제에 실패했습니다.')
    }
  }
}

onMounted(() => {
  fetchHomeworks()
  fetchAcademies()
  fetchClasses()
})
</script>

<template>
  <div class="teacher-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#e6a23c">
              <Notebook />
            </el-icon>
            숙제 관리
          </h1>
          <p style="margin: 8px 0 0; color: #909399">숙제를 생성하고 관리합니다</p>
        </div>
        <el-button type="primary" @click="openAddDialog" :icon="Plus" size="large">
          숙제 추가
        </el-button>
      </div>
    </el-card>

    <!-- Search and Filters -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
            v-model="searchQuery"
            placeholder="숙제명, 학원, 반으로 검색"
            :prefix-icon="Search"
            clearable
            size="large"
          />
        </el-col>
        <el-col :span="4">
          <el-button @click="fetchHomeworks" :icon="Refresh" size="large">
            새로고침
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Homeworks Table -->
    <el-card shadow="never">
      <el-table
        :data="tableData"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="title" label="숙제명" min-width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#e6a23c">
                <Notebook />
              </el-icon>
              <span style="font-weight: 500">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="questionCount" label="문제 수" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.questionCount || 0 }}문제</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="academyName" label="학원" width="150">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#67c23a">
                <OfficeBuilding />
              </el-icon>
              {{ row.academyName }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="className" label="반" width="150">
          <template #default="{ row }">
            <el-tag type="success">{{ row.className }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="memo" label="메모" min-width="150">
          <template #default="{ row }">
            <span style="color: #606266">{{ row.memo || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="마감일" width="140">
          <template #default="{ row }">
            <div v-if="row.dueDate" style="display: flex; align-items: center; gap: 8px">
              <el-icon :color="isOverdue(row.dueDate) ? '#f56c6c' : isDueSoon(row.dueDate) ? '#e6a23c' : '#67c23a'">
                <Clock />
              </el-icon>
              <span
                style="font-size: 13px; font-weight: 500"
                :style="{ color: isOverdue(row.dueDate) ? '#f56c6c' : isDueSoon(row.dueDate) ? '#e6a23c' : '#606266' }"
              >
                {{ formatDate(row.dueDate) }}
              </span>
            </div>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>

        <el-table-column label="등록일" width="150">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#909399">
                <Calendar />
              </el-icon>
              <span style="font-size: 13px">
                {{ row.createdAt ? new Date(row.createdAt).toLocaleDateString('ko-KR') : '-' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="관리" width="170" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="문제 구성" placement="top">
              <el-button
                size="small"
                type="success"
                circle
                @click="openComposer(row)"
              >
                <el-icon><Collection /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="수정" placement="top">
              <el-button
                size="small"
                type="primary"
                circle
                @click="openEditDialog(row)"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="삭제" placement="top">
              <el-button
                size="small"
                type="danger"
                circle
                @click="handleDelete(row)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <HomeworkProblemsComposer
        v-if="composerHomeworkId !== null"
        v-model:visible="composerVisible"
        :homework-id="composerHomeworkId"
        :homework-title="composerHomeworkTitle"
        @saved="fetchHomeworks"
      />

      <div style="margin-top: 16px; display: flex; justify-content: space-between; align-items: center">
        <span style="color: #909399; font-size: 14px">
          전체 {{ totalItems }}개
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalItems"
          layout="sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editMode ? '숙제 정보 수정' : '새 숙제 추가'"
      width="600px"
      :before-close="() => dialogVisible = false"
    >
      <el-form
        :model="currentHomework"
        label-width="100px"
        label-position="left"
      >
        <el-form-item label="숙제명" required>
          <el-input
            v-model="currentHomework.title"
            placeholder="숙제명을 입력하세요"
          />
        </el-form-item>

        <el-form-item label="학원" required>
          <el-select
            v-model="currentHomework.academyId"
            placeholder="학원을 선택하세요"
            style="width: 100%"
            disabled
          >
            <el-option
              v-for="academy in academies"
              :key="academy.id"
              :label="academy.name"
              :value="academy.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="반" required>
          <el-select
            v-model="currentHomework.classId"
            placeholder="반을 선택하세요"
            :disabled="!currentHomework.academyId"
            style="width: 100%"
          >
            <el-option
              v-for="cls in availableClasses"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="문제 수" required>
          <el-input-number
            v-model="currentHomework.questionCount"
            :min="1"
            :max="200"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="메모">
          <el-input
            v-model="currentHomework.memo"
            type="textarea"
            :rows="4"
            placeholder="메모를 입력하세요 (선택사항)"
          />
        </el-form-item>

        <el-form-item label="마감일">
          <el-date-picker
            v-model="currentHomework.dueDate"
            type="date"
            placeholder="마감일을 선택하세요"
            format="YYYY년 MM월 DD일"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span>
          <el-button @click="dialogVisible = false">취소</el-button>
          <el-button
            type="primary"
            @click="handleSubmit"
            :disabled="!currentHomework.title || !currentHomework.questionCount || !currentHomework.academyId || !currentHomework.classId"
          >
            {{ editMode ? '수정' : '추가' }}
          </el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.el-card {
  border-radius: 8px;
}

.el-table {
  border-radius: 8px;
  overflow: hidden;
}
</style>
