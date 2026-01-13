<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clinicAPI, academyClassAPI, type Clinic, type AcademyClass } from '../api/client'

const router = useRouter()
const loading = ref(false)
const clinics = ref<Clinic[]>([])
const academyClasses = ref<AcademyClass[]>([])
const selectedClassId = ref<number | undefined>(undefined)
const createDialogVisible = ref(false)

const filteredClinics = computed(() => {
  if (!selectedClassId.value) return clinics.value
  return clinics.value.filter(c => c.classId === selectedClassId.value)
})

const fetchAcademyClasses = async () => {
  try {
    const response = await academyClassAPI.getAcademyClasses()
    academyClasses.value = response.data.content || response.data

    // Select first class by default
    if (academyClasses.value.length > 0 && !selectedClassId.value) {
      selectedClassId.value = academyClasses.value[0].id
      fetchClinics()
    }
  } catch (error) {
    ElMessage.error('반 목록을 불러오는데 실패했습니다.')
  }
}

const fetchClinics = async () => {
  if (!selectedClassId.value) return

  loading.value = true
  try {
    const response = await clinicAPI.getClinicsByClass(selectedClassId.value)
    clinics.value = response.data
  } catch (error) {
    ElMessage.error('클리닉 목록을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const createClinicForThisWeek = async () => {
  if (!selectedClassId.value) {
    ElMessage.error('반을 선택해주세요.')
    return
  }

  try {
    await clinicAPI.createClinicForThisWeek(selectedClassId.value)
    ElMessage.success('이번주 클리닉이 생성되었습니다.')
    fetchClinics()
  } catch (error: any) {
    const message = error.response?.data?.message || '클리닉 생성에 실패했습니다.'
    ElMessage.error(message)
  }
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
}

const formatTime = (timeStr: string) => {
  return timeStr.substring(0, 5) // HH:mm
}

const getStatusTag = (status: string) => {
  return status === 'OPEN' ? 'success' : 'info'
}

const getStatusText = (status: string) => {
  return status === 'OPEN' ? '신청 가능' : '종료됨'
}

const viewClinicDetail = (clinic: Clinic) => {
  router.push(`/clinics/${clinic.id}`)
}

const closeClinic = async (clinic: Clinic) => {
  if (!clinic.id) return

  try {
    await ElMessageBox.confirm(
      '클리닉을 종료하시겠습니까? 종료 후에는 학생들이 신청할 수 없습니다.',
      '클리닉 종료',
      {
        confirmButtonText: '종료',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await clinicAPI.closeClinic(clinic.id)
    ElMessage.success('클리닉이 종료되었습니다.')
    fetchClinics()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('클리닉 종료에 실패했습니다.')
    }
  }
}

const deleteClinic = async (clinic: Clinic) => {
  if (!clinic.id) return

  try {
    await ElMessageBox.confirm(
      `${formatDate(clinic.clinicDate)} 클리닉을 삭제하시겠습니까?`,
      '삭제 확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await clinicAPI.deleteClinic(clinic.id)
    ElMessage.success('클리닉이 삭제되었습니다.')
    fetchClinics()
  } catch (error: any) {
    if (error !== 'cancel') {
      const message = error.response?.data?.message || '삭제에 실패했습니다.'
      ElMessage.error(message)
    }
  }
}

onMounted(() => {
  fetchAcademyClasses()
})
</script>

<template>
  <div class="teacher-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#67c23a">
              <MagicStick />
            </el-icon>
            클리닉 관리
          </h1>
          <p style="margin: 8px 0 0; color: #909399">완성도가 낮은 학생들을 위한 보충 수업을 관리합니다</p>
        </div>
        <div style="display: flex; gap: 12px; align-items: center">
          <el-select
            v-model="selectedClassId"
            placeholder="반 선택"
            style="width: 200px"
            @change="fetchClinics"
          >
            <el-option
              v-for="cls in academyClasses"
              :key="cls.id"
              :label="`${cls.academyName} - ${cls.name}`"
              :value="cls.id"
            />
          </el-select>
          <el-button type="primary" @click="createClinicForThisWeek" :disabled="!selectedClassId">
            <el-icon style="margin-right: 8px"><Plus /></el-icon>
            이번주 클리닉 생성
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Clinics List -->
    <el-card shadow="never">
      <el-table
        :data="filteredClinics"
        v-loading="loading"
        style="width: 100%"
      >
        <el-table-column prop="clinicDate" label="날짜" width="250">
          <template #default="{ row }">
            {{ formatDate(row.clinicDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="clinicTime" label="시간" width="100">
          <template #default="{ row }">
            {{ formatTime(row.clinicTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="className" label="반" width="200" />
        <el-table-column prop="registrationCount" label="신청자 수" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="primary" round>{{ row.registrationCount }}명</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="300" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewClinicDetail(row)">
              <el-icon style="margin-right: 4px"><View /></el-icon>
              상세보기
            </el-button>
            <el-button
              size="small"
              type="warning"
              @click="closeClinic(row)"
              :disabled="row.status === 'CLOSED'"
            >
              종료
            </el-button>
            <el-button size="small" type="danger" @click="deleteClinic(row)">삭제</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filteredClinics.length === 0" description="등록된 클리닉이 없습니다" />
    </el-card>
  </div>
</template>
