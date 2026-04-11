<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentAPI, academyAPI, academyClassAPI, type Student, type Academy, type AcademyClass } from '../api/client'
import { usePagination } from '../composables/usePagination'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const students = ref<Student[]>([])
const academies = ref<Academy[]>([])
const allClasses = ref<AcademyClass[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const editMode = ref(false)
const currentStudent = ref<Student>({ name: '', grade: '', school: '', academyId: undefined, classId: undefined })
const { currentPage, pageSize } = usePagination('students-view')

const availableClasses = computed(() => {
  if (!currentStudent.value.academyId) return []
  return allClasses.value.filter(cls => cls.academyId === currentStudent.value.academyId)
})

const filteredData = computed(() => {
  if (!searchQuery.value) return students.value
  return students.value.filter(student =>
    (student.name || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (student.grade || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (student.school || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (student.academyName || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
    (student.className || '').toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

const totalItems = computed(() => filteredData.value.length)

watch(() => currentStudent.value.academyId, () => {
  currentStudent.value.classId = undefined
})

const fetchStudents = async () => {
  loading.value = true
  try {
    const response = await studentAPI.getStudents({ size: 10000 })
    students.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('학생 목록을 불러오는데 실패했습니다.')
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
  currentStudent.value = {
    name: '',
    grade: '',
    school: '',
    academyId: authStore.activeAcademyId ?? undefined,
    classId: undefined
  }
  dialogVisible.value = true
}

const openEditDialog = (student: Student) => {
  editMode.value = true
  currentStudent.value = { ...student }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!currentStudent.value.academyId || !currentStudent.value.classId) {
    ElMessage.error('학원과 반을 선택해주세요.')
    return
  }

  if (!editMode.value && !currentStudent.value.pin) {
    ElMessage.error('PIN을 입력해주세요.')
    return
  }

  if (currentStudent.value.pin && currentStudent.value.pin.length !== 4) {
    ElMessage.error('PIN은 4자리 숫자여야 합니다.')
    return
  }

  try {
    if (editMode.value && currentStudent.value.id) {
      await studentAPI.updateStudent(currentStudent.value.id, currentStudent.value)

      // PIN이 입력된 경우 별도로 업데이트
      if (currentStudent.value.pin && currentStudent.value.pin.trim() !== '') {
        await studentAPI.resetPin(currentStudent.value.id, currentStudent.value.pin)
      }

      ElMessage.success('학생 정보가 수정되었습니다.')
    } else {
      await studentAPI.createStudent(currentStudent.value)
      ElMessage.success('학생이 추가되었습니다.')
    }
    dialogVisible.value = false
    fetchStudents()
  } catch (error) {
    ElMessage.error('작업을 완료할 수 없습니다.')
  }
}

const handleDelete = async (student: Student) => {
  if (!student.id) return

  try {
    await ElMessageBox.confirm(
      `${student.name} 학생을 삭제하시겠습니까?`,
      '삭제 확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await studentAPI.deleteStudent(student.id)
    ElMessage.success('학생이 삭제되었습니다.')
    fetchStudents()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('삭제에 실패했습니다.')
    }
  }
}

const navigateToDetail = (studentId: number) => {
  router.push(`/students/${studentId}`)
}

onMounted(() => {
  fetchStudents()
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
            <el-icon size="32" color="#409eff">
              <User />
            </el-icon>
            학생 관리
          </h1>
          <p style="margin: 8px 0 0; color: #909399">학생 정보를 등록하고 관리합니다</p>
        </div>
        <el-button type="primary" @click="openAddDialog" :icon="Plus" size="large">
          학생 추가
        </el-button>
      </div>
    </el-card>

    <!-- Search and Filters -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
            v-model="searchQuery"
            placeholder="학생명, 학년, 학교, 학원으로 검색"
            :prefix-icon="Search"
            clearable
            size="large"
          />
        </el-col>
        <el-col :span="4">
          <el-button @click="fetchStudents" :icon="Refresh" size="large">
            새로고침
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Students Table -->
    <el-card shadow="never">
      <el-table
        :data="tableData"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.id }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="학생명" min-width="120">
          <template #default="{ row }">
            <div
              style="display: flex; align-items: center; gap: 8px; cursor: pointer"
              @click="navigateToDetail(row.id)"
            >
              <el-avatar size="small" :icon="UserFilled" />
              <span style="font-weight: 500; color: #409eff">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="grade" label="학년" min-width="100">
          <template #default="{ row }">
            <el-tag type="info">{{ row.grade }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="school" label="학교" min-width="150">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#909399">
                <School />
              </el-icon>
              {{ row.school }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="academyName" label="학원" min-width="150">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#67c23a">
                <OfficeBuilding />
              </el-icon>
              {{ row.academyName }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="className" label="반" min-width="150">
          <template #default="{ row }">
            <el-tag type="success">{{ row.className }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="작업" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              circle
              @click="openEditDialog(row)"
            >
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button
              size="small"
              type="danger"
              circle
              @click="handleDelete(row)"
            >
              <el-icon><Delete /></el-icon>
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

    <!-- Add/Edit Dialog -->
    <el-dialog 
      v-model="dialogVisible"
      :title="editMode ? '학생 정보 수정' : '새 학생 추가'"
      width="500px"
      :before-close="() => dialogVisible = false"
    >
      <el-form 
        :model="currentStudent" 
        label-width="80px"
        label-position="left"
      >
        <el-form-item label="학생명" required>
          <el-input 
            v-model="currentStudent.name" 
            placeholder="학생명을 입력하세요"
            :prefix-icon="User"
          />
        </el-form-item>
        
        <el-form-item label="학년" required>
          <el-input
            v-model="currentStudent.grade"
            placeholder="학년을 입력하세요 (예: 중1, 고2)"
          />
        </el-form-item>

        <el-form-item label="학교" required>
          <el-input
            v-model="currentStudent.school"
            placeholder="학교명을 입력하세요"
          />
        </el-form-item>

        <el-form-item label="학원" required>
          <el-select
            v-model="currentStudent.academyId"
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
            v-model="currentStudent.classId"
            placeholder="반을 선택하세요"
            :disabled="!currentStudent.academyId"
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

        <el-form-item :label="editMode ? 'PIN (선택)' : 'PIN'" :required="!editMode">
          <el-input
            v-model="currentStudent.pin"
            :placeholder="editMode ? 'PIN을 변경하려면 입력하세요 (4자리)' : 'PIN 번호를 입력하세요 (4자리)'"
            maxlength="4"
            show-password
          />
          <template v-if="editMode">
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
              비워두면 PIN이 변경되지 않습니다
            </div>
          </template>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span>
          <el-button @click="dialogVisible = false">취소</el-button>
          <el-button
            type="primary"
            @click="handleSubmit"
            :disabled="!currentStudent.name || !currentStudent.grade || !currentStudent.school || !currentStudent.academyId || !currentStudent.classId"
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