<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import { clinicAPI, studentHomeworkAPI, type ClinicDetail, type StudentClinicHomework, type HomeworkProgress, type ClinicHomeworkProgress } from '../api/client'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const clinicDetail = ref<ClinicDetail | null>(null)
const progressData = ref<ClinicHomeworkProgress[]>([])
const editingHomeworkMap = ref<Record<string, boolean>>({})
const editIncorrectQuestionsMap = ref<Record<string, string[]>>({})
const editUnsolvedQuestionsMap = ref<Record<string, string[]>>({})
const incorrectTagInput = ref<Record<string, string>>({})
const unsolvedTagInput = ref<Record<string, string>>({})

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
    await fetchClinicProgress()
  } catch (error) {
    ElMessage.error('클리닉 상세 정보를 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const fetchClinicProgress = async () => {
  try {
    const response = await clinicAPI.getClinicProgress(clinicId.value)
    progressData.value = response.data
  } catch (error) {
    // Progress data is optional
    progressData.value = []
  }
}

const getProgress = (studentId: number, homeworkId: number): ClinicHomeworkProgress | undefined => {
  return progressData.value.find(
    p => p.studentId === studentId && p.homeworkId === homeworkId
  )
}

const startClinic = async () => {
  try {
    await ElMessageBox.confirm(
      '클리닉을 시작하시겠습니까? 현재 모든 학생의 미완성 숙제 상태가 "전" 스냅샷으로 저장됩니다.',
      '클리닉 시작',
      {
        confirmButtonText: '시작',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await clinicAPI.startClinic(clinicId.value)
    ElMessage.success('클리닉이 시작되었습니다.')
    await fetchClinicDetail()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('클리닉 시작에 실패했습니다.')
    }
  }
}

const endClinic = async () => {
  try {
    await ElMessageBox.confirm(
      '클리닉을 종료하시겠습니까? 현재 모든 학생의 미완성 숙제 상태가 "후" 스냅샷으로 저장됩니다.',
      '클리닉 종료',
      {
        confirmButtonText: '종료',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await clinicAPI.endClinic(clinicId.value)
    ElMessage.success('클리닉이 종료되었습니다. 변화량을 확인하세요.')
    await fetchClinicDetail()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('클리닉 종료에 실패했습니다.')
    }
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

const parseQuestions = (str?: string): string[] => {
  if (!str) return []
  return str.split(',').map(s => s.trim()).filter(s => s !== '')
}

const expandRange = (input: string): string[] => {
  const results: string[] = []
  const parts = input.split(',').map(s => s.trim()).filter(s => s !== '')
  for (const part of parts) {
    const rangeMatch = part.match(/^(\d+)\s*[~\-]\s*(\d+)$/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1]!)
      const end = parseInt(rangeMatch[2]!)
      const [lo, hi] = start <= end ? [start, end] : [end, start]
      for (let i = lo; i <= hi; i++) results.push(String(i))
    } else if (/^\d+$/.test(part)) {
      results.push(part)
    }
  }
  return results
}

const addTag = (map: Record<string, string[]>, key: string, inputMap: Record<string, string>) => {
  const raw = inputMap[key] || ''
  const nums = expandRange(raw)
  if (nums.length === 0) return
  const existing = map[key] || []
  const merged = [...new Set([...existing, ...nums])]
  merged.sort((a, b) => Number(a) - Number(b))
  map[key] = merged
  inputMap[key] = ''
}

const removeTag = (map: Record<string, string[]>, key: string, index: number) => {
  const tags = [...(map[key] || [])]
  tags.splice(index, 1)
  map[key] = tags
}

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('복사되었습니다')
  } catch {
    ElMessage.error('복사에 실패했습니다')
  }
}

const startEditingHomework = (studentId: number, homework: HomeworkProgress) => {
  const key = getHomeworkKey(studentId, homework.homeworkId)
  editingHomeworkMap.value[key] = true
  editIncorrectQuestionsMap.value[key] = parseQuestions(homework.incorrectQuestions)
  editUnsolvedQuestionsMap.value[key] = parseQuestions(homework.unsolvedQuestions)
  incorrectTagInput.value[key] = ''
  unsolvedTagInput.value[key] = ''
}

const cancelEditingHomework = (studentId: number, homeworkId: number) => {
  const key = getHomeworkKey(studentId, homeworkId)
  delete editingHomeworkMap.value[key]
  delete editIncorrectQuestionsMap.value[key]
  delete editUnsolvedQuestionsMap.value[key]
  delete incorrectTagInput.value[key]
  delete unsolvedTagInput.value[key]
}

const saveHomework = async (studentId: number, homework: HomeworkProgress) => {
  const key = getHomeworkKey(studentId, homework.homeworkId)
  const incorrectTags = editIncorrectQuestionsMap.value[key] || []
  const unsolvedTags = editUnsolvedQuestionsMap.value[key] || []
  const incorrectCount = incorrectTags.length
  const unsolvedCount = unsolvedTags.length
  const totalQuestions = homework.questionCount

  // Validation: 합 초과
  if (incorrectCount + unsolvedCount > totalQuestions) {
    ElMessage.error(`오답과 안 푼 문제의 합(${incorrectCount + unsolvedCount})이 전체 문제 수(${totalQuestions})를 초과할 수 없습니다.`)
    return
  }

  // Validation: 중복 문항
  const overlap = incorrectTags.filter(t => unsolvedTags.includes(t))
  if (overlap.length > 0) {
    ElMessage.error(`${overlap.join(', ')}번 문항이 오답과 안 푼 문제에 중복됩니다.`)
    return
  }

  const incorrectQuestions = incorrectTags.join(',') || undefined
  const unsolvedQuestions = unsolvedTags.join(',') || undefined

  try {
    await studentHomeworkAPI.updateIncorrectCount(
      studentId,
      homework.homeworkId,
      incorrectCount,
      unsolvedCount,
      incorrectQuestions,
      unsolvedQuestions
    )
    ElMessage.success('숙제 정보가 업데이트되었습니다.')
    cancelEditingHomework(studentId, homework.homeworkId)
    fetchClinicDetail()
  } catch (error) {
    ElMessage.error('업데이트에 실패했습니다.')
  }
}

const toggleFollowUp = async (studentId: number, homework: HomeworkProgress) => {
  const prev = homework.followUpFlag
  const newValue = !prev
  homework.followUpFlag = newValue  // optimistic update
  try {
    await studentHomeworkAPI.setFollowUp(studentId, homework.homeworkId, newValue)
    ElMessage.success(newValue ? 'RED 표시했습니다' : '표시를 해제했습니다')
  } catch (error) {
    homework.followUpFlag = prev  // rollback on failure
    ElMessage.error('마킹 변경에 실패했습니다')
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
          <div style="display: flex; gap: 12px">
            <el-button type="success" @click="startClinic">
              <el-icon style="margin-right: 4px"><VideoPlay /></el-icon>
              클리닉 시작
            </el-button>
            <el-button type="warning" @click="endClinic">
              <el-icon style="margin-right: 4px"><VideoPause /></el-icon>
              클리닉 종료
            </el-button>
            <el-button @click="router.push('/clinics')">목록으로</el-button>
          </div>
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
            <el-table-column label="오답 문항" min-width="200">
              <template #default="{ row }">
                <div v-if="editingHomeworkMap[getHomeworkKey(student.studentId, row.homeworkId)]">
                  <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center">
                    <el-tag
                      v-for="(tag, idx) in editIncorrectQuestionsMap[getHomeworkKey(student.studentId, row.homeworkId)] || []"
                      :key="tag"
                      closable
                      type="danger"
                      size="small"
                      @close="removeTag(editIncorrectQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), idx)"
                    >
                      {{ tag }}번
                    </el-tag>
                    <el-input
                      :model-value="incorrectTagInput[getHomeworkKey(student.studentId, row.homeworkId)] || ''"
                      @update:model-value="(val: string) => incorrectTagInput[getHomeworkKey(student.studentId, row.homeworkId)] = val"
                      @keyup.enter="addTag(editIncorrectQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), incorrectTagInput)"
                      @keyup.space.prevent="addTag(editIncorrectQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), incorrectTagInput)"
                      placeholder="번호 입력"
                      size="small"
                      style="width: 80px"
                    />
                  </div>
                </div>
                <div v-else style="display: flex; align-items: center; gap: 6px">
                  <span v-if="row.incorrectCount !== null && row.incorrectCount !== undefined" style="font-weight: 600; color: #f56c6c">
                    {{ row.incorrectCount }}개
                  </span>
                  <el-tag v-else type="info" size="small">미입력</el-tag>
                  <el-button v-if="row.incorrectQuestions" :icon="DocumentCopy" size="small" link @click="copyToClipboard(row.incorrectQuestions)" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="안 푼 문항" min-width="200">
              <template #default="{ row }">
                <div v-if="editingHomeworkMap[getHomeworkKey(student.studentId, row.homeworkId)]">
                  <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center">
                    <el-tag
                      v-for="(tag, idx) in editUnsolvedQuestionsMap[getHomeworkKey(student.studentId, row.homeworkId)] || []"
                      :key="tag"
                      closable
                      type="warning"
                      size="small"
                      @close="removeTag(editUnsolvedQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), idx)"
                    >
                      {{ tag }}번
                    </el-tag>
                    <el-input
                      :model-value="unsolvedTagInput[getHomeworkKey(student.studentId, row.homeworkId)] || ''"
                      @update:model-value="(val: string) => unsolvedTagInput[getHomeworkKey(student.studentId, row.homeworkId)] = val"
                      @keyup.enter="addTag(editUnsolvedQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), unsolvedTagInput)"
                      @keyup.space.prevent="addTag(editUnsolvedQuestionsMap, getHomeworkKey(student.studentId, row.homeworkId), unsolvedTagInput)"
                      placeholder="번호 입력"
                      size="small"
                      style="width: 80px"
                    />
                  </div>
                </div>
                <div v-else style="display: flex; align-items: center; gap: 6px">
                  <span v-if="row.unsolvedCount !== null && row.unsolvedCount !== undefined" style="font-weight: 600; color: #e6a23c">
                    {{ row.unsolvedCount }}개
                  </span>
                  <span v-else-if="row.incorrectCount !== null && row.incorrectCount !== undefined" style="color: #909399">0개</span>
                  <el-tag v-else type="info" size="small">미입력</el-tag>
                  <el-button v-if="row.unsolvedQuestions" :icon="DocumentCopy" size="small" link @click="copyToClipboard(row.unsolvedQuestions)" />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="completion" label="완성도" width="100" align="center">
              <template #default="{ row }">
                <span :style="{ color: getCompletionColor(row.completion), fontWeight: 600 }">
                  {{ row.completion || 0 }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column label="클리닉 변화" width="180" align="center">
              <template #default="{ row }">
                <div v-if="getProgress(student.studentId, row.homeworkId)" style="font-size: 13px">
                  <div style="display: flex; align-items: center; justify-content: center; gap: 8px">
                    <span style="color: #909399">
                      {{ getProgress(student.studentId, row.homeworkId)!.completionBefore }}%
                    </span>
                    <el-icon><Right /></el-icon>
                    <span :style="{ color: getCompletionColor(getProgress(student.studentId, row.homeworkId)!.completionAfter), fontWeight: 600 }">
                      {{ getProgress(student.studentId, row.homeworkId)!.completionAfter ?? '-' }}%
                    </span>
                  </div>
                  <div v-if="getProgress(student.studentId, row.homeworkId)!.completionChange !== null && getProgress(student.studentId, row.homeworkId)!.completionChange !== undefined"
                       :style="{
                         color: getProgress(student.studentId, row.homeworkId)!.completionChange! >= 0 ? '#67c23a' : '#f56c6c',
                         fontWeight: 600,
                         marginTop: '4px'
                       }">
                    {{ getProgress(student.studentId, row.homeworkId)!.completionChange! >= 0 ? '+' : '' }}{{ getProgress(student.studentId, row.homeworkId)!.completionChange }}%p
                  </div>
                </div>
                <span v-else style="color: #909399; font-size: 13px">-</span>
              </template>
            </el-table-column>
            <el-table-column label="숙제 다시내기" width="140" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.followUpFlag ? 'danger' : 'info'"
                  :effect="row.followUpFlag ? 'dark' : 'plain'"
                  style="cursor: pointer; user-select: none"
                  @click="toggleFollowUp(student.studentId, row)"
                >
                  {{ row.followUpFlag ? '재제출' : '정상' }}
                </el-tag>
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
