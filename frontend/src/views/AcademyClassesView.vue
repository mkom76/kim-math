<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { academyClassAPI, academyAPI, type AcademyClass, type Academy } from '../api/client'

const router = useRouter()
const loading = ref(false)
const academyClasses = ref<AcademyClass[]>([])
const academies = ref<Academy[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const editMode = ref(false)
const currentClass = ref<AcademyClass>({ name: '', academyId: undefined })
const currentPage = ref(1)
const pageSize = ref(10)

const filteredData = computed(() => {
  if (!searchQuery.value) return academyClasses.value
  return academyClasses.value.filter(cls =>
    (cls.name || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (cls.academyName || '').toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

const totalItems = computed(() => filteredData.value.length)

const fetchAcademyClasses = async () => {
  loading.value = true
  try {
    const response = await academyClassAPI.getAcademyClasses({ size: 10000 })
    academyClasses.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('반 목록을 불러오는데 실패했습니다.')
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

const dayOfWeekOptions = [
  { label: '월요일', value: 'MONDAY' },
  { label: '화요일', value: 'TUESDAY' },
  { label: '수요일', value: 'WEDNESDAY' },
  { label: '목요일', value: 'THURSDAY' },
  { label: '금요일', value: 'FRIDAY' },
  { label: '토요일', value: 'SATURDAY' },
  { label: '일요일', value: 'SUNDAY' }
]

const openAddDialog = () => {
  editMode.value = false
  currentClass.value = { name: '', academyId: undefined, clinicDayOfWeek: undefined, clinicTime: undefined }
  dialogVisible.value = true
}

const openEditDialog = (cls: AcademyClass) => {
  editMode.value = true
  currentClass.value = { ...cls }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!currentClass.value.academyId) {
    ElMessage.error('학원을 선택해주세요.')
    return
  }

  try {
    if (editMode.value && currentClass.value.id) {
      await academyClassAPI.updateAcademyClass(currentClass.value.id, currentClass.value)
      ElMessage.success('반 정보가 수정되었습니다.')
    } else {
      await academyClassAPI.createAcademyClass(currentClass.value)
      ElMessage.success('반이 추가되었습니다.')
    }
    dialogVisible.value = false
    fetchAcademyClasses()
  } catch (error) {
    ElMessage.error('작업을 완료할 수 없습니다.')
  }
}

const handleDelete = async (cls: AcademyClass) => {
  if (!cls.id) return

  try {
    await ElMessageBox.confirm(
      `${cls.name} 반을 삭제하시겠습니까?`,
      '삭제 확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await academyClassAPI.deleteAcademyClass(cls.id)
    ElMessage.success('반이 삭제되었습니다.')
    fetchAcademyClasses()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('삭제에 실패했습니다.')
    }
  }
}

const navigateToLessons = (cls: AcademyClass) => {
  router.push(`/lessons?academyId=${cls.academyId}&classId=${cls.id}`)
}

onMounted(() => {
  fetchAcademyClasses()
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
              <Grid />
            </el-icon>
            반 관리
          </h1>
          <p style="margin: 8px 0 0; color: #909399">학원별 반 정보를 관리합니다</p>
        </div>
        <div style="display: flex; gap: 12px">
          <el-input
            v-model="searchQuery"
            placeholder="반명 또는 학원명 검색"
            clearable
            style="width: 250px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="openAddDialog">
            <el-icon style="margin-right: 8px"><Plus /></el-icon>
            반 추가
          </el-button>
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
        <el-table-column prop="academyName" label="학원" width="150" />
        <el-table-column prop="name" label="반명" />
        <el-table-column label="작업" width="250" align="center">
          <template #default="{ row }">
            <el-button size="small" type="info" @click="navigateToLessons(row)">
              <el-icon style="margin-right: 4px"><Calendar /></el-icon>
              수업 보기
            </el-button>
            <el-button size="small" @click="openEditDialog(row)">수정</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">삭제</el-button>
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
      :title="editMode ? '반 수정' : '반 추가'"
      width="500px"
    >
      <el-form :model="currentClass" label-width="120px">
        <el-form-item label="학원">
          <el-select
            v-model="currentClass.academyId"
            placeholder="학원을 선택하세요"
            style="width: 100%"
          >
            <el-option
              v-for="academy in academies"
              :key="academy.id"
              :label="academy.name"
              :value="academy.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="반명">
          <el-input v-model="currentClass.name" placeholder="반명을 입력하세요" />
        </el-form-item>
        <el-divider content-position="left">
          <span style="color: #909399; font-size: 14px">클리닉 설정</span>
        </el-divider>
        <el-form-item label="클리닉 요일">
          <el-select
            v-model="currentClass.clinicDayOfWeek"
            placeholder="요일 선택 (선택사항)"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="option in dayOfWeekOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="클리닉 시간">
          <el-time-picker
            v-model="currentClass.clinicTime"
            placeholder="시간 선택 (선택사항)"
            format="HH:mm"
            value-format="HH:mm:ss"
            style="width: 100%"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" @click="handleSubmit">
          {{ editMode ? '수정' : '추가' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
