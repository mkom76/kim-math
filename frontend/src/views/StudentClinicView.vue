<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clinicAPI, authAPI, type StudentClinicInfo, type AuthResponse } from '../api/client'

const loading = ref(false)
const clinicInfo = ref<StudentClinicInfo | null>(null)
const currentUser = ref<AuthResponse | null>(null)

const isMobile = ref(false)
const h1FontSize = computed(() => isMobile.value ? '16px' : '28px')
const h3FontSize = computed(() => isMobile.value ? '13px' : '18px')

const hasUpcomingClinic = computed(() => {
  return clinicInfo.value?.upcomingClinic != null
})

const isRegistered = computed(() => {
  return clinicInfo.value?.myRegistration != null &&
         clinicInfo.value.myRegistration.status !== 'CANCELLED'
})

const shouldAttendClinic = computed(() => {
  return clinicInfo.value?.shouldAttend === true
})

const fetchCurrentUser = async () => {
  try {
    const response = await authAPI.getCurrentUser()
    currentUser.value = response.data
  } catch (error) {
    ElMessage.error('사용자 정보를 불러오는데 실패했습니다.')
  }
}

const fetchClinicInfo = async () => {
  if (!currentUser.value?.userId) return

  loading.value = true
  try {
    const response = await clinicAPI.getStudentClinicInfo(currentUser.value.userId)
    clinicInfo.value = response.data
  } catch (error) {
    ElMessage.error('클리닉 정보를 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
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

const getCompletionColor = (completion?: number) => {
  if (!completion) return '#909399'
  if (completion >= 90) return '#67c23a'
  if (completion >= 70) return '#e6a23c'
  return '#f56c6c'
}

const registerForClinic = async () => {
  if (!currentUser.value?.userId || !clinicInfo.value?.upcomingClinic?.id) return

  try {
    await ElMessageBox.confirm(
      '클리닉에 신청하시겠습니까?',
      '신청 확인',
      {
        confirmButtonText: '신청',
        cancelButtonText: '취소',
        type: 'info',
      }
    )

    await clinicAPI.registerForClinic(clinicInfo.value.upcomingClinic.id, currentUser.value.userId)
    ElMessage.success('클리닉 신청이 완료되었습니다.')
    fetchClinicInfo()
  } catch (error) {
    if (error !== 'cancel') {
      const message = (error as any).response?.data?.message || '신청에 실패했습니다.'
      ElMessage.error(message)
    }
  }
}

const cancelRegistration = async () => {
  if (!currentUser.value?.userId || !clinicInfo.value?.upcomingClinic?.id) return

  try {
    await ElMessageBox.confirm(
      '클리닉 신청을 취소하시겠습니까?',
      '취소 확인',
      {
        confirmButtonText: '취소',
        cancelButtonText: '돌아가기',
        type: 'warning',
      }
    )

    await clinicAPI.cancelRegistration(clinicInfo.value.upcomingClinic.id, currentUser.value.userId)
    ElMessage.success('클리닉 신청이 취소되었습니다.')
    fetchClinicInfo()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('취소에 실패했습니다.')
    }
  }
}

onMounted(async () => {
  // Mobile detection
  const checkMobile = () => {
    isMobile.value = window.innerWidth < 768
  }
  checkMobile()
  window.addEventListener('resize', checkMobile)

  await fetchCurrentUser()
  await fetchClinicInfo()
})
</script>

<template>
  <div class="student-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <h1 :style="{ margin: 0, fontSize: h1FontSize, fontWeight: 600, color: '#303133', display: 'flex', alignItems: 'center', gap: '12px' }">
        <el-icon size="32" color="#67c23a">
          <MagicStick />
        </el-icon>
        클리닉 관리
      </h1>
      <p style="margin: 8px 0 0; color: #909399">완성도가 낮은 숙제를 집중적으로 보충하는 시간입니다</p>
    </el-card>

    <div v-loading="loading">
      <!-- No Upcoming Clinic -->
      <el-card v-if="!hasUpcomingClinic" shadow="never">
        <el-empty description="예정된 클리닉이 없습니다">
          <template #image>
            <el-icon size="80" color="#909399">
              <Calendar />
            </el-icon>
          </template>
        </el-empty>
      </el-card>

      <div v-else>
        <!-- Should Attend Warning -->
        <el-alert
          v-if="shouldAttendClinic && !isRegistered"
          title="클리닉 신청을 권장합니다"
          type="warning"
          :closable="false"
          style="margin-bottom: 24px"
        >
          <template #default>
            <p style="margin: 0">완성도가 90% 미만인 숙제가 있습니다. 클리닉에 참여하여 부족한 부분을 보충하세요!</p>
          </template>
        </el-alert>

        <!-- Clinic Info -->
        <el-card shadow="never" style="margin-bottom: 24px">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <h3 :style="{ margin: 0, fontSize: h3FontSize, fontWeight: 600 }">
                다가오는 클리닉
              </h3>
              <el-tag v-if="isRegistered" type="success" size="large">신청 완료</el-tag>
              <el-tag v-else type="info" size="large">미신청</el-tag>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="날짜">
              <span :style="{ fontWeight: 600, fontSize: h3FontSize }">
                {{ formatDate(clinicInfo!.upcomingClinic!.clinicDate) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="시간">
              <span :style="{ fontWeight: 600, fontSize: h3FontSize }">
                {{ formatTime(clinicInfo!.upcomingClinic!.clinicTime) }}
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 24px; text-align: center">
            <el-button
              v-if="!isRegistered"
              type="primary"
              size="large"
              @click="registerForClinic"
              style="min-width: 200px"
            >
              <el-icon style="margin-right: 8px"><Check /></el-icon>
              클리닉 신청하기
            </el-button>
            <el-button
              v-else
              type="danger"
              size="large"
              @click="cancelRegistration"
              style="min-width: 200px"
            >
              <el-icon style="margin-right: 8px"><Close /></el-icon>
              신청 취소하기
            </el-button>
          </div>
        </el-card>

        <!-- Incomplete Homeworks -->
        <el-card v-if="clinicInfo.incompleteHomeworks.length > 0" shadow="never">
          <template #header>
            <h3 :style="{ margin: 0, fontSize: h3FontSize, fontWeight: 600, color: '#e6a23c' }">
              <el-icon style="margin-right: 8px"><Warning /></el-icon>
              완성도가 낮은 숙제 ({{ clinicInfo.incompleteHomeworks.length }}개)
            </h3>
          </template>

          <el-table :data="clinicInfo.incompleteHomeworks" style="width: 100%">
            <el-table-column prop="homeworkTitle" label="숙제" />
            <el-table-column prop="lessonDate" label="수업 날짜" width="150" />
            <el-table-column prop="completion" label="완성도" width="120" align="center">
              <template #default="{ row }">
                <span :style="{ color: getCompletionColor(row.completion), fontWeight: 600, fontSize: h3FontSize }">
                  {{ row.completion || 0 }}%
                </span>
              </template>
            </el-table-column>
          </el-table>

          <el-alert
            type="info"
            :closable="false"
            style="margin-top: 16px"
          >
            <template #default>
              <p style="margin: 0">클리닉에서 위 숙제들을 집중적으로 복습하고 보충할 예정입니다.</p>
            </template>
          </el-alert>
        </el-card>

        <!-- All Homeworks Complete -->
        <el-card v-else shadow="never">
          <el-result icon="success" title="모든 숙제를 잘 완성했습니다!">
            <template #sub-title>
              모든 숙제의 완성도가 90% 이상입니다. 계속 열심히 해주세요!
            </template>
          </el-result>
        </el-card>
      </div>
    </div>
  </div>
</template>
