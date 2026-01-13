<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clinicAPI, studentHomeworkAPI, type ClinicDetail, type StudentClinicHomework, type HomeworkProgress } from '../api/client'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const clinicDetail = ref<ClinicDetail | null>(null)
const editingHomeworkMap = ref<Record<string, boolean>>({})
const editIncorrectCountMap = ref<Record<string, number>>({})
const editUnsolvedCountMap = ref<Record<string, number>>({})

const clinicId = computed(() => Number(route.params.id))

const registeredStudents = computed(() => {
  if (!clinicDetail.value) return []
  return clinicDetail.value.students.filter(s => s.registration && s.registration.status !== 'CANCELLED')
})

const unregisteredStudents = computed(() => {
  if (!clinicDetail.value) return []
  return clinicDetail.value.students.filter(s => !s.registration || s.registration.status === 'CANCELLED')
})

const fetchClinicDetail = async () => {
  loading.value = true
  try {
    const response = await clinicAPI.getClinicDetail(clinicId.value)
    clinicDetail.value = response.data
  } catch (error) {
    ElMessage.error('클리닉 상세 정보를 불러오는데 실패했습니다.')
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

const getHomeworkKey = (studentId: number, homeworkId: number) => {
  return `${studentId}-${homeworkId}`
}

const startEditingHomework = (studentId: number, homework: HomeworkProgress) => {
  const key = getHomeworkKey(studentId, homework.homeworkId)
  editingHomeworkMap.value[key] = true
  editIncorrectCountMap.value[key] = homework.incorrectCount || 0
  editUnsolvedCountMap.value[key] = homework.unsolvedCount || 0
}

const cancelEditingHomework = (studentId: number, homeworkId: number) => {
  const key = getHomeworkKey(studentId, homeworkId)
  delete editingHomeworkMap.value[key]
  delete editIncorrectCountMap.value[key]
  delete editUnsolvedCountMap.value[key]
}

const saveHomework = async (studentId: number, homework: HomeworkProgress) => {
  const key = getHomeworkKey(studentId, homework.homeworkId)
  const incorrectCount = editIncorrectCountMap.value[key] || 0
  const unsolvedCount = editUnsolvedCountMap.value[key] || 0

  try {
    await studentHomeworkAPI.updateIncorrectCount(
      studentId,
      homework.homeworkId,
      incorrectCount,
      unsolvedCount
    )
    ElMessage.success('숙제 정보가 업데이트되었습니다.')
    delete editingHomeworkMap.value[key]
    delete editIncorrectCountMap.value[key]
    delete editUnsolvedCountMap.value[key]
    fetchClinicDetail()
  } catch (error) {
    ElMessage.error('업데이트에 실패했습니다.')
  }
}

const updateAttendance = async (registrationId: number, status: string) => {
  try {
    await clinicAPI.updateAttendance(registrationId, status)
    ElMessage.success('참석 상태가 업데이트되었습니다.')
    fetchClinicDetail()
  } catch (error) {
    ElMessage.error('업데이트에 실패했습니다.')
  }
}

const getStatusTag = (status: string) => {
  if (status === 'ATTENDED') return 'success'
  if (status === 'REGISTERED') return 'primary'
  return 'info'
}

const getStatusText = (status: string) => {
  if (status === 'ATTENDED') return '참석'
  if (status === 'REGISTERED') return '신청'
  return '취소'
}

onMounted(() => {
  fetchClinicDetail()
})
</script>

<template>
  <div class="teacher-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 style="margin: 0; font-size: 20px; font-weight: 600">클리닉 상세</h2>
          <el-button @click="router.push('/clinics')">목록으로</el-button>
        </div>
      </template>

      <div v-if="clinicDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="날짜">
            {{ formatDate(clinicDetail.clinic.clinicDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="시간">
            {{ formatTime(clinicDetail.clinic.clinicTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="반">
            {{ clinicDetail.clinic.className }}
          </el-descriptions-item>
          <el-descriptions-item label="신청자 수">
            <el-tag type="primary" round>{{ registeredStudents.length }}명</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- Registered Students -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <h3 style="margin: 0; font-size: 18px; font-weight: 600; color: #67c23a">
          <el-icon style="margin-right: 8px"><Check /></el-icon>
          신청자 ({{ registeredStudents.length }}명)
        </h3>
      </template>

      <div v-for="student in registeredStudents" :key="student.studentId" style="margin-bottom: 32px; padding-bottom: 32px; border-bottom: 1px solid #ebeef5">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <div>
            <h4 style="margin: 0; font-size: 16px; font-weight: 600">{{ student.studentName }}</h4>
            <div style="margin-top: 4px">
              <el-tag :type="getStatusTag(student.registration!.status)" size="small">
                {{ getStatusText(student.registration!.status) }}
              </el-tag>
            </div>
          </div>
          <div style="display: flex; gap: 8px">
            <el-button
              size="small"
              type="success"
              @click="updateAttendance(student.registration!.id!, 'ATTENDED')"
              :disabled="student.registration!.status === 'ATTENDED'"
            >
              참석 체크
            </el-button>
          </div>
        </div>

        <!-- Homework List -->
        <div v-if="student.homeworks.length > 0">
          <el-table :data="student.homeworks" style="width: 100%">
            <el-table-column prop="homeworkTitle" label="숙제" width="250" />
            <el-table-column prop="lessonDate" label="수업 날짜" width="150" />
            <el-table-column prop="incorrectCount" label="오답 개수" width="120" align="center">
              <template #default="{ row }">
                <div v-if="!editingHomeworkMap[getHomeworkKey(student.studentId, row.homeworkId)]">
                  {{ row.incorrectCount || 0 }}개
                </div>
                <el-input-number
                  v-else
                  v-model="editIncorrectCountMap[getHomeworkKey(student.studentId, row.homeworkId)]"
                  :min="0"
                  :max="row.questionCount"
                  size="small"
                  style="width: 100px"
                />
              </template>
            </el-table-column>
            <el-table-column prop="unsolvedCount" label="안 푼 문제" width="120" align="center">
              <template #default="{ row }">
                <div v-if="!editingHomeworkMap[getHomeworkKey(student.studentId, row.homeworkId)]">
                  {{ row.unsolvedCount || 0 }}개
                </div>
                <el-input-number
                  v-else
                  v-model="editUnsolvedCountMap[getHomeworkKey(student.studentId, row.homeworkId)]"
                  :min="0"
                  :max="row.questionCount"
                  size="small"
                  style="width: 100px"
                />
              </template>
            </el-table-column>
            <el-table-column prop="completion" label="완성도" width="120" align="center">
              <template #default="{ row }">
                <span :style="{ color: getCompletionColor(row.completion), fontWeight: 600 }">
                  {{ row.completion || 0 }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column label="작업" width="180" align="center">
              <template #default="{ row }">
                <div v-if="!editingHomeworkMap[getHomeworkKey(student.studentId, row.homeworkId)]">
                  <el-button size="small" @click="startEditingHomework(student.studentId, row)">
                    편집
                  </el-button>
                </div>
                <div v-else style="display: flex; gap: 4px">
                  <el-button size="small" type="primary" @click="saveHomework(student.studentId, row)">
                    저장
                  </el-button>
                  <el-button size="small" @click="cancelEditingHomework(student.studentId, row.homeworkId)">
                    취소
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="미제출 숙제가 없습니다" :image-size="60" />
      </div>

      <el-empty v-if="registeredStudents.length === 0" description="신청한 학생이 없습니다" />
    </el-card>

    <!-- Unregistered Students (완성도 낮은 학생들) -->
    <el-card shadow="never">
      <template #header>
        <h3 style="margin: 0; font-size: 18px; font-weight: 600; color: #909399">
          <el-icon style="margin-right: 8px"><User /></el-icon>
          미신청 학생 ({{ unregisteredStudents.length }}명)
        </h3>
      </template>

      <div v-for="student in unregisteredStudents" :key="student.studentId" style="margin-bottom: 24px; padding-bottom: 24px; border-bottom: 1px solid #ebeef5">
        <div style="margin-bottom: 12px">
          <h4 style="margin: 0; font-size: 16px; font-weight: 600; color: #606266">{{ student.studentName }}</h4>
          <div v-if="student.homeworks.length > 0" style="margin-top: 8px">
            <el-tag type="warning" size="small">완성도 낮은 숙제: {{ student.homeworks.length }}개</el-tag>
          </div>
        </div>

        <div v-if="student.homeworks.length > 0">
          <el-table :data="student.homeworks" size="small" style="width: 100%">
            <el-table-column prop="homeworkTitle" label="숙제" width="250" />
            <el-table-column prop="completion" label="완성도" width="120" align="center">
              <template #default="{ row }">
                <span :style="{ color: getCompletionColor(row.completion), fontWeight: 600 }">
                  {{ row.completion || 0 }}%
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else style="color: #67c23a; font-size: 14px">
          모든 숙제를 90% 이상 완성했습니다
        </div>
      </div>

      <el-empty v-if="unregisteredStudents.length === 0" description="모든 학생이 신청했습니다" />
    </el-card>
  </div>
</template>
