<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, User, UserFilled } from '@element-plus/icons-vue'
import {
  studentAPI,
  academyAPI,
  academyClassAPI,
  type Student,
  type Academy,
  type AcademyClass,
  type StudentCreateResponse,
} from '../api/client'
import { usePagination } from '../composables/usePagination'
import { useAuthStore } from '@/stores/auth'
import StudentBulkImportDialog from '../components/StudentBulkImportDialog.vue'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const students = ref<Student[]>([])
const academies = ref<Academy[]>([])
const allClasses = ref<AcademyClass[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const bulkDialogVisible = ref(false)
const consentDialogVisible = ref(false)
const createdConsent = ref<StudentCreateResponse | null>(null)
const editMode = ref(false)
const currentStudent = ref<Student>({ name: '', grade: '', school: '', academyId: undefined, classId: undefined })
const { currentPage, pageSize } = usePagination('students-view')

const availableClasses = computed(() => {
  if (!currentStudent.value.academyId) return []
  return allClasses.value.filter(cls => cls.academyId === currentStudent.value.academyId)
})

const bulkCandidateClasses = computed(() =>
  allClasses.value.filter(c => c.academyId === authStore.activeAcademyId)
)
const existingStudentNames = computed(() => students.value.map(s => s.name))

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
const consentLink = computed(() =>
  createdConsent.value
    ? `${window.location.origin}/consent/${createdConsent.value.consentToken}`
    : ''
)
const canSubmitStudent = computed(() => {
  const student = currentStudent.value
  const commonFieldsPresent = Boolean(
    student.name && student.grade && student.school && student.academyId && student.classId
  )
  if (!commonFieldsPresent) return false
  return editMode.value || Boolean(student.parentName && student.parentPhone)
})

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
    parentName: '',
    parentPhone: '',
    contactPhone: '',
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

  if (!editMode.value && (!currentStudent.value.parentName || !currentStudent.value.parentPhone)) {
    ElMessage.error('보호자 이름과 휴대폰을 입력해주세요.')
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
      const response = await studentAPI.createStudent({
        name: currentStudent.value.name,
        grade: currentStudent.value.grade,
        school: currentStudent.value.school,
        academyId: currentStudent.value.academyId,
        classId: currentStudent.value.classId!,
        parentName: currentStudent.value.parentName!,
        parentPhone: currentStudent.value.parentPhone!.replace(/[^0-9-]/g, ''),
        contactPhone: currentStudent.value.contactPhone?.replace(/[^0-9-]/g, ''),
      })
      createdConsent.value = response.data
      consentDialogVisible.value = true
      ElMessage.success('학생이 추가되었습니다. 동의 링크를 보호자에게 전달하세요.')
    }
    dialogVisible.value = false
    fetchStudents()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '작업을 완료할 수 없습니다.')
  }
}

const copyConsentLink = async () => {
  if (!consentLink.value) return
  try {
    await navigator.clipboard.writeText(consentLink.value)
    ElMessage.success('동의 링크가 클립보드에 복사되었습니다.')
  } catch {
    ElMessage.error('복사하지 못했습니다. 링크를 직접 선택해 복사해주세요.')
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
        <div style="display: flex; gap: 8px;">
          <el-button
            v-if="!authStore.isAssistant"
            @click="bulkDialogVisible = true"
            size="large"
          >
            일괄 등록
          </el-button>
          <el-button v-if="!authStore.isAssistant" type="primary" @click="openAddDialog" :icon="Plus" size="large">
            학생 추가
          </el-button>
        </div>
      </div>
    </el-card>

    <StudentBulkImportDialog
      v-model:visible="bulkDialogVisible"
      :classes="bulkCandidateClasses"
      :existing-names="existingStudentNames"
      @imported="fetchStudents"
    />

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

        <template v-if="!editMode">
          <el-form-item label="보호자명" required>
            <el-input
              v-model="currentStudent.parentName"
              placeholder="동의할 보호자 이름을 입력하세요"
            />
          </el-form-item>

          <el-form-item label="보호자 전화" required>
            <el-input
              v-model="currentStudent.parentPhone"
              placeholder="010-1234-5678"
            />
          </el-form-item>

          <el-form-item label="학생 전화">
            <el-input
              v-model="currentStudent.contactPhone"
              placeholder="선택 입력"
            />
          </el-form-item>

          <el-alert type="info" :closable="false" style="margin-bottom: 18px">
            학생은 보호자 동의 완료 후 로그인할 수 있으며, 초기 PIN은 보호자 휴대폰 뒤 4자리입니다.
          </el-alert>
        </template>

        <el-form-item v-if="editMode" label="PIN (선택)">
          <el-input
            v-model="currentStudent.pin"
            placeholder="PIN을 변경하려면 입력하세요 (4자리)"
            maxlength="4"
            show-password
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            비워두면 PIN이 변경되지 않습니다
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span>
          <el-button @click="dialogVisible = false">취소</el-button>
          <el-button
            type="primary"
            @click="handleSubmit"
            :disabled="!canSubmitStudent"
          >
            {{ editMode ? '수정' : '추가' }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog
      v-model="consentDialogVisible"
      title="보호자 동의 링크"
      width="560px"
      @closed="createdConsent = null"
    >
      <el-alert type="success" :closable="false" style="margin-bottom: 16px">
        {{ createdConsent?.name }} 학생이 등록되었습니다. 아래 링크를 보호자에게 전달하세요.
      </el-alert>
      <el-input :model-value="consentLink" readonly>
        <template #append>
          <el-button @click="copyConsentLink">복사</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="consentDialogVisible = false">확인</el-button>
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
