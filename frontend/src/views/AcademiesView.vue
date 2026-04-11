<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { academyAPI, type Academy } from '../api/client'
import { usePagination } from '../composables/usePagination'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const academies = ref<Academy[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const currentAcademy = ref<Academy>({ name: '' })
const { currentPage, pageSize } = usePagination('academies-view')

const filteredData = computed(() => {
  if (!searchQuery.value) return academies.value
  return academies.value.filter(academy =>
    (academy.name || '').toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

const totalItems = computed(() => filteredData.value.length)

const canEdit = (academy: Academy) => {
  if (!academy.id) return false
  const membership = authStore.memberships.find(m => m.academyId === academy.id)
  return membership?.role === 'ACADEMY_ADMIN'
}

const fetchAcademies = async () => {
  loading.value = true
  try {
    const response = await academyAPI.getAcademies({ size: 10000 })
    academies.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('학원 목록을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const openEditDialog = (academy: Academy) => {
  currentAcademy.value = { ...academy }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!currentAcademy.value.id) return
  try {
    await academyAPI.updateAcademy(currentAcademy.value.id, currentAcademy.value)
    ElMessage.success('학원 정보가 수정되었습니다.')
    dialogVisible.value = false
    fetchAcademies()
  } catch (error) {
    ElMessage.error('작업을 완료할 수 없습니다.')
  }
}

const navigateToLessons = (academy: Academy) => {
  router.push(`/lessons?academyId=${academy.id}`)
}

onMounted(() => {
  fetchAcademies()
})
</script>

<template>
  <div class="teacher-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#409eff">
              <OfficeBuilding />
            </el-icon>
            학원 관리
          </h1>
          <p style="margin: 8px 0 0; color: #909399">학원 정보를 관리합니다</p>
        </div>
        <div style="display: flex; gap: 12px">
          <el-input
            v-model="searchQuery"
            placeholder="학원명 검색"
            clearable
            style="width: 250px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>
    </el-card>

    <!-- Table -->
    <el-card shadow="never">
      <el-table
        :data="tableData"
        v-loading="loading"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="학원명" />
        <el-table-column label="작업" width="200" align="center">
          <template #default="{ row }">
            <el-button size="small" type="info" @click="navigateToLessons(row)">
              <el-icon style="margin-right: 4px"><Calendar /></el-icon>
              수업 보기
            </el-button>
            <el-button
              v-if="canEdit(row)"
              size="small"
              @click="openEditDialog(row)"
            >
              수정
            </el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <el-dialog
      v-model="dialogVisible"
      title="학원 수정"
      width="500px"
    >
      <el-form :model="currentAcademy" label-width="100px">
        <el-form-item label="학원명">
          <el-input v-model="currentAcademy.name" placeholder="학원명을 입력하세요" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" @click="handleSubmit">수정</el-button>
      </template>
    </el-dialog>
  </div>
</template>
