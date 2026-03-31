<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineSquare, BellFilled, VideoPlay, Plus, Top, Bottom, Delete, DocumentCopy } from '@element-plus/icons-vue'
import { lessonAPI, testAPI, homeworkAPI, studentHomeworkAPI, lessonVideoAPI, type Lesson, type Test, type Homework, type LessonStudentStats, type StudentHomeworkAssignment, type LessonVideo, type VideoStats, type AttendanceRecord, type AttendanceRequest } from '../api/client'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const lesson = ref<Lesson | null>(null)
const stats = ref<LessonStudentStats | null>(null)
const unattachedTests = ref<Test[]>([])
const unattachedHomeworks = ref<Homework[]>([])
const attachTestDialogVisible = ref(false)
const attachHomeworkDialogVisible = ref(false)
const selectedTestId = ref<number | undefined>(undefined)
const selectedHomeworkId = ref<number | undefined>(undefined)
const editingContent = ref(false)
const editCommonFeedback = ref('')
const editAnnouncement = ref('')
const editingDate = ref(false)
const editLessonDate = ref('')
const editingHomeworkMap = ref<Map<number, boolean>>(new Map())
const editIncorrectCountMap = ref<Map<number, number>>(new Map())
const editUnsolvedCountMap = ref<Map<number, number>>(new Map())
const editIncorrectQuestionsMap = ref<Map<number, string[]>>(new Map())
const editUnsolvedQuestionsMap = ref<Map<number, string[]>>(new Map())
const incorrectTagInput = ref<Map<number, string>>(new Map())
const unsolvedTagInput = ref<Map<number, string>>(new Map())

// 여러 숙제 관리를 위한 state
const lessonHomeworks = ref<Homework[]>([])
const addHomeworkDialogVisible = ref(false)

// 학생별 숙제 할당을 위한 state
const studentAssignments = ref<StudentHomeworkAssignment[]>([])
const bulkAssignHomeworkId = ref<number | undefined>(undefined)

const lessonId = computed(() => Number(route.params.id))

const fetchLesson = async () => {
  loading.value = true
  try {
    const response = await lessonAPI.getLesson(lessonId.value)
    lesson.value = response.data

    // Fetch student stats
    const statsResponse = await lessonAPI.getLessonStats(lessonId.value)
    stats.value = statsResponse.data

    // Fetch lesson homeworks
    await fetchLessonHomeworks()

    // Fetch student assignments
    await fetchAssignments()

    // Fetch attendance
    await loadAttendance()
  } catch (error) {
    ElMessage.error('수업 정보를 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const fetchLessonHomeworks = async () => {
  if (!lessonId.value) return
  try {
    const response = await lessonAPI.getLessonHomeworks(lessonId.value)
    lessonHomeworks.value = response.data
  } catch (error) {
    ElMessage.error('숙제 목록을 불러오는데 실패했습니다.')
  }
}

const fetchAssignments = async () => {
  if (!lessonId.value) return
  try {
    const response = await lessonAPI.getAssignments(lessonId.value)
    studentAssignments.value = response.data
  } catch (error) {
    ElMessage.error('할당 현황을 불러오는데 실패했습니다.')
  }
}

const fetchUnattachedTests = async () => {
  if (!lesson.value) return
  try {
    const response = await testAPI.getUnattachedTests(lesson.value.academyId!, lesson.value.classId!)
    unattachedTests.value = response.data
  } catch (error) {
    ElMessage.error('연결 가능한 시험 목록을 불러오는데 실패했습니다.')
  }
}

const fetchUnattachedHomeworks = async () => {
  if (!lesson.value) return
  try {
    const response = await homeworkAPI.getUnattachedHomeworks(lesson.value.academyId!, lesson.value.classId!)
    unattachedHomeworks.value = response.data
  } catch (error) {
    ElMessage.error('연결 가능한 숙제 목록을 불러오는데 실패했습니다.')
  }
}

const openAttachTestDialog = async () => {
  await fetchUnattachedTests()
  selectedTestId.value = undefined
  attachTestDialogVisible.value = true
}

const openAttachHomeworkDialog = async () => {
  await fetchUnattachedHomeworks()
  selectedHomeworkId.value = undefined
  attachHomeworkDialogVisible.value = true
}

const openAddHomeworkDialog = async () => {
  await fetchUnattachedHomeworks()
  selectedHomeworkId.value = undefined
  addHomeworkDialogVisible.value = true
}

const handleAttachTest = async () => {
  if (!selectedTestId.value) {
    ElMessage.error('시험을 선택해주세요.')
    return
  }

  try {
    await lessonAPI.attachTest(lessonId.value, selectedTestId.value)
    ElMessage.success('시험이 연결되었습니다.')
    attachTestDialogVisible.value = false
    fetchLesson()
  } catch (error: any) {
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('시험 연결에 실패했습니다.')
    }
  }
}

const handleAttachHomework = async () => {
  if (!selectedHomeworkId.value) {
    ElMessage.error('숙제를 선택해주세요.')
    return
  }

  try {
    await lessonAPI.attachHomework(lessonId.value, selectedHomeworkId.value)
    ElMessage.success('숙제가 연결되었습니다.')
    attachHomeworkDialogVisible.value = false
    addHomeworkDialogVisible.value = false
    fetchLesson()
  } catch (error: any) {
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('숙제 연결에 실패했습니다.')
    }
  }
}

const handleDetachTest = async () => {
  try {
    await ElMessageBox.confirm(
      '이 시험과의 연결을 해제하시겠습니까?',
      '확인',
      {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await lessonAPI.detachTest(lessonId.value)
    ElMessage.success('시험 연결이 해제되었습니다.')
    fetchLesson()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('시험 연결 해제에 실패했습니다.')
    }
  }
}

const handleRemoveHomework = async (homeworkId: number) => {
  try {
    await ElMessageBox.confirm(
      '이 숙제와의 연결을 해제하시겠습니까? 학생별 할당 정보도 함께 삭제됩니다.',
      '확인',
      {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await lessonAPI.removeHomework(lessonId.value, homeworkId)
    ElMessage.success('숙제 연결이 해제되었습니다.')
    fetchLesson()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('숙제 연결 해제에 실패했습니다.')
    }
  }
}

const handleBulkAssign = async () => {
  if (!bulkAssignHomeworkId.value) {
    ElMessage.error('숙제를 선택해주세요.')
    return
  }

  try {
    await ElMessageBox.confirm(
      '선택한 숙제를 모든 학생에게 할당하시겠습니까?',
      '확인',
      {
        confirmButtonText: '확인',
        cancelButtonText: '취소',
        type: 'info',
      }
    )

    // Create assignments map: studentId -> homeworkId
    const assignments: Record<number, number> = {}
    studentAssignments.value.forEach((assignment) => {
      assignments[assignment.studentId] = bulkAssignHomeworkId.value!
    })

    await lessonAPI.assignHomeworks(lessonId.value, assignments)
    ElMessage.success('숙제가 모든 학생에게 할당되었습니다.')
    fetchAssignments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('일괄 할당에 실패했습니다.')
    }
  }
}

const saveAssignments = async () => {
  try {
    // Create assignments map from current studentAssignments
    const assignments: Record<number, number> = {}
    studentAssignments.value.forEach((assignment) => {
      if (assignment.assignedHomeworkId) {
        assignments[assignment.studentId] = assignment.assignedHomeworkId
      }
    })

    // Check if there are any unassigned students
    const unassignedCount = studentAssignments.value.filter(
      (a) => !a.assignedHomeworkId
    ).length

    if (unassignedCount > 0) {
      await ElMessageBox.confirm(
        `${unassignedCount}명의 학생이 아직 숙제를 할당받지 못했습니다. 계속하시겠습니까?`,
        '경고',
        {
          confirmButtonText: '계속',
          cancelButtonText: '취소',
          type: 'warning',
        }
      )
    }

    await lessonAPI.assignHomeworks(lessonId.value, assignments)
    ElMessage.success('숙제 할당이 저장되었습니다.')
    fetchAssignments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('숙제 할당 저장에 실패했습니다.')
    }
  }
}

const navigateToTestDetail = (testId: number) => {
  router.push(`/tests/${testId}`)
}

const navigateToHomeworkDetail = (homeworkId: number) => {
  router.push(`/homeworks/${homeworkId}`)
}

const startEditingContent = () => {
  editCommonFeedback.value = lesson.value?.commonFeedback || ''
  editAnnouncement.value = lesson.value?.announcement || ''
  editingContent.value = true
}

const cancelEditingContent = () => {
  editingContent.value = false
  editCommonFeedback.value = ''
  editAnnouncement.value = ''
}

const saveContent = async () => {
  try {
    await lessonAPI.updateLessonContent(lessonId.value, editCommonFeedback.value, editAnnouncement.value)
    ElMessage.success('수업 내용이 저장되었습니다.')
    editingContent.value = false
    fetchLesson()
  } catch (error) {
    ElMessage.error('수업 내용 저장에 실패했습니다.')
  }
}

const startEditingDate = () => {
  editLessonDate.value = lesson.value?.lessonDate || ''
  editingDate.value = true
}

const cancelEditingDate = () => {
  editingDate.value = false
  editLessonDate.value = ''
}

const saveDate = async () => {
  if (!editLessonDate.value) {
    ElMessage.error('날짜를 선택해주세요.')
    return
  }

  try {
    await lessonAPI.updateLessonDate(lessonId.value, editLessonDate.value)
    ElMessage.success('수업 날짜가 변경되었습니다.')
    editingDate.value = false
    fetchLesson()
  } catch (error: any) {
    const message = error.response?.data?.message || '수업 날짜 변경에 실패했습니다.'
    ElMessage.error(message)
  }
}

const copyToClipboard = (str: string) => {
  navigator.clipboard.writeText(str)
  ElMessage.success('클립보드에 복사되었습니다')
}

const parseQuestions = (str: string | undefined | null): string[] => {
  if (!str) return []
  return str.split(',').map(s => s.trim()).filter(s => s !== '')
}

const startEditingHomework = (studentId: number, row: StudentHomeworkAssignment) => {
  editingHomeworkMap.value.set(studentId, true)
  editIncorrectCountMap.value.set(studentId, row.incorrectCount || 0)
  editUnsolvedCountMap.value.set(studentId, row.unsolvedCount || 0)
  editIncorrectQuestionsMap.value.set(studentId, parseQuestions(row.incorrectQuestions))
  editUnsolvedQuestionsMap.value.set(studentId, parseQuestions(row.unsolvedQuestions))
  incorrectTagInput.value.set(studentId, '')
  unsolvedTagInput.value.set(studentId, '')
}

const cancelEditingHomework = (studentId: number) => {
  editingHomeworkMap.value.delete(studentId)
  editIncorrectCountMap.value.delete(studentId)
  editUnsolvedCountMap.value.delete(studentId)
  editIncorrectQuestionsMap.value.delete(studentId)
  editUnsolvedQuestionsMap.value.delete(studentId)
  incorrectTagInput.value.delete(studentId)
  unsolvedTagInput.value.delete(studentId)
}

const expandRange = (input: string): string[] => {
  const results: string[] = []
  const parts = input.split(',').map(s => s.trim()).filter(s => s !== '')
  for (const part of parts) {
    const rangeMatch = part.match(/^(\d+)\s*[~\-]\s*(\d+)$/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1])
      const end = parseInt(rangeMatch[2])
      const [lo, hi] = start <= end ? [start, end] : [end, start]
      for (let i = lo; i <= hi; i++) results.push(String(i))
    } else if (/^\d+$/.test(part)) {
      results.push(part)
    }
  }
  return results
}

const addTag = (map: Map<number, string[]>, countMap: Map<number, number>, studentId: number, inputMap: Map<number, string>) => {
  const raw = inputMap.get(studentId) || ''
  const nums = expandRange(raw)
  if (nums.length === 0) return
  const existing = map.get(studentId) || []
  const merged = [...new Set([...existing, ...nums])]
  merged.sort((a, b) => Number(a) - Number(b))
  map.set(studentId, merged)
  countMap.set(studentId, merged.length)
  inputMap.set(studentId, '')
}

const removeTag = (map: Map<number, string[]>, countMap: Map<number, number>, studentId: number, index: number) => {
  const tags = [...(map.get(studentId) || [])]
  tags.splice(index, 1)
  map.set(studentId, tags)
  countMap.set(studentId, tags.length)
}

const saveHomeworkIncorrectCount = async (studentId: number, homeworkId: number, totalQuestions: number) => {
  const incorrectTags = editIncorrectQuestionsMap.value.get(studentId) || []
  const unsolvedTags = editUnsolvedQuestionsMap.value.get(studentId) || []
  const incorrectCount = incorrectTags.length
  const unsolvedCount = unsolvedTags.length

  // Validation
  if (incorrectCount + unsolvedCount > totalQuestions) {
    ElMessage.error(`오답과 안 푼 문제의 합(${incorrectCount + unsolvedCount})이 전체 문제 수(${totalQuestions})를 초과할 수 없습니다.`)
    return
  }

  // 중복 문항 체크
  const overlap = incorrectTags.filter(t => unsolvedTags.includes(t))
  if (overlap.length > 0) {
    ElMessage.error(`${overlap.join(', ')}번 문항이 오답과 안 푼 문제에 중복됩니다.`)
    return
  }

  const incorrectQuestions = incorrectTags.join(',') || undefined
  const unsolvedQuestions = unsolvedTags.join(',') || undefined

  try {
    await studentHomeworkAPI.updateIncorrectCount(studentId, homeworkId, incorrectCount, unsolvedCount, incorrectQuestions, unsolvedQuestions)
    ElMessage.success('숙제 완성도가 업데이트되었습니다.')
    cancelEditingHomework(studentId)
    fetchLesson()
  } catch (error) {
    ElMessage.error('숙제 완성도 업데이트에 실패했습니다.')
  }
}

const calculateAverageCompletion = () => {
  const assignedStudents = studentAssignments.value.filter(s => s.assignedHomeworkId)
  if (assignedStudents.length === 0) return 0

  const completedStudents = assignedStudents.filter(s => s.completion !== null && s.completion !== undefined)
  if (completedStudents.length === 0) return 0

  const total = completedStudents.reduce((sum, s) => sum + (s.completion || 0), 0)
  return Math.round(total / completedStudents.length)
}

// Video management state
const videos = ref<LessonVideo[]>([])
const videoStats = ref<VideoStats[]>([])
const addVideoDialogVisible = ref(false)
const videoStatsDialogVisible = ref(false)
const selectedVideoStats = ref<VideoStats | null>(null)
const youtubeUrl = ref('')

const fetchVideos = async () => {
  try {
    const response = await lessonVideoAPI.getVideos(lessonId.value)
    videos.value = response.data

    // Also fetch video stats
    if (videos.value.length > 0) {
      fetchVideoStats()
    }
  } catch (error) {
    // Videos are optional, don't show error
    videos.value = []
  }
}

const fetchVideoStats = async () => {
  try {
    const response = await lessonVideoAPI.getVideoStats(lessonId.value)
    videoStats.value = response.data
  } catch (error) {
    console.error('Failed to fetch video stats:', error)
    videoStats.value = []
  }
}

const addVideo = async () => {
  try {
    await lessonVideoAPI.addVideo(lessonId.value, { youtubeUrl: youtubeUrl.value })
    ElMessage.success('영상이 추가되었습니다')
    addVideoDialogVisible.value = false
    youtubeUrl.value = ''
    fetchVideos()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '영상 추가에 실패했습니다')
  }
}

const moveUp = async (video: LessonVideo) => {
  try {
    await lessonVideoAPI.updateOrder(lessonId.value, video.id, video.orderIndex - 1)
    fetchVideos()
  } catch (error) {
    ElMessage.error('순서 변경에 실패했습니다')
  }
}

const moveDown = async (video: LessonVideo) => {
  try {
    await lessonVideoAPI.updateOrder(lessonId.value, video.id, video.orderIndex + 1)
    fetchVideos()
  } catch (error) {
    ElMessage.error('순서 변경에 실패했습니다')
  }
}

const deleteVideo = async (video: LessonVideo) => {
  try {
    await ElMessageBox.confirm('영상을 삭제하시겠습니까?', '확인')
    await lessonVideoAPI.deleteVideo(lessonId.value, video.id)
    ElMessage.success('영상이 삭제되었습니다')
    fetchVideos()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('영상 삭제에 실패했습니다')
    }
  }
}

const showVideoStats = (video: LessonVideo) => {
  const stats = videoStats.value.find(s => s.videoId === video.id)
  if (stats) {
    selectedVideoStats.value = stats
    videoStatsDialogVisible.value = true
  } else {
    ElMessage.warning('시청 현황 정보를 찾을 수 없습니다')
  }
}

const formatDuration = (duration: string) => {
  const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/)
  if (!match) return duration

  const hours = parseInt(match[1] || '0')
  const minutes = parseInt(match[2] || '0')
  const seconds = parseInt(match[3] || '0')

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

// 출석 체크
const attendanceList = ref<AttendanceRecord[]>([])
const attendanceLoading = ref(false)
const attendanceSaving = ref(false)

const attendanceLabels: Record<string, string> = {
  PRESENT: '출석',
  ABSENT: '결석',
  LATE: '지각',
  EARLY_LEAVE: '조퇴',
  VIDEO: '인강',
}

const attendanceColors: Record<string, string> = {
  PRESENT: '#67C23A',
  ABSENT: '#F56C6C',
  LATE: '#E6A23C',
  EARLY_LEAVE: '#909399',
  VIDEO: '#409EFF',
}

const loadAttendance = async () => {
  if (!lesson.value) return
  attendanceLoading.value = true
  try {
    attendanceList.value = await lessonAPI.getAttendance(lesson.value.id!)
  } finally {
    attendanceLoading.value = false
  }
}

const saveAttendance = async () => {
  if (!lesson.value) return
  attendanceSaving.value = true
  try {
    const requests: AttendanceRequest[] = attendanceList.value.map(a => ({
      studentId: a.studentId,
      status: a.attendanceStatus,
    }))
    await lessonAPI.saveAttendance(lesson.value.id!, requests)
    ElMessage.success('출석이 저장되었습니다')
  } catch {
    ElMessage.error('출석 저장에 실패했습니다')
  } finally {
    attendanceSaving.value = false
  }
}

const setAllAttendance = (status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'VIDEO') => {
  attendanceList.value.forEach(a => {
    a.attendanceStatus = status
  })
}

onMounted(() => {
  fetchLesson()
  fetchVideos()
})
</script>

<template>
  <div class="teacher-view" v-loading="loading" style="padding: 24px; max-width: 1200px; margin: 0 auto">
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 style="margin: 0; font-size: 24px; font-weight: 600">수업 상세</h2>
          <el-button @click="router.push('/lessons')">목록으로</el-button>
        </div>
      </template>

      <div v-if="lesson">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="수업 날짜">
            <div v-if="!editingDate" style="display: flex; justify-content: space-between; align-items: center">
              <span>
                {{ new Date(lesson.lessonDate).toLocaleDateString('ko-KR', {
                  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
                }) }}
              </span>
              <el-button type="primary" size="small" @click="startEditingDate">
                날짜 변경
              </el-button>
            </div>
            <div v-else style="display: flex; gap: 8px; align-items: center">
              <el-date-picker
                v-model="editLessonDate"
                type="date"
                placeholder="날짜 선택"
                value-format="YYYY-MM-DD"
                style="flex: 1"
              />
              <el-button type="primary" size="small" @click="saveDate">저장</el-button>
              <el-button size="small" @click="cancelEditingDate">취소</el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="학원">
            {{ lesson.academyName }}
          </el-descriptions-item>
          <el-descriptions-item label="반">
            {{ lesson.className }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- Lesson Content: Feedback and Announcement -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0; font-size: 18px; font-weight: 600">수업 내용</h3>
          <el-button v-if="!editingContent" type="primary" size="small" @click="startEditingContent">
            편집
          </el-button>
          <div v-else style="display: flex; gap: 8px">
            <el-button size="small" @click="cancelEditingContent">취소</el-button>
            <el-button type="primary" size="small" @click="saveContent">저장</el-button>
          </div>
        </div>
      </template>

      <div v-if="lesson">
        <!-- View Mode -->
        <div v-if="!editingContent">
          <div style="margin-bottom: 24px">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
              <el-icon size="18" color="#409eff"><ChatLineSquare /></el-icon>
              <span style="font-weight: 600; font-size: 16px">수업 공통 피드백</span>
            </div>
            <div style="padding: 16px; background: #f5f7fa; border-radius: 8px; white-space: pre-wrap; line-height: 1.6">
              {{ lesson.commonFeedback || '피드백이 작성되지 않았습니다.' }}
            </div>
          </div>

          <div>
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
              <el-icon size="18" color="#e6a23c"><BellFilled /></el-icon>
              <span style="font-weight: 600; font-size: 16px">공지사항</span>
            </div>
            <div style="padding: 16px; background: #fef0f0; border-radius: 8px; white-space: pre-wrap; line-height: 1.6">
              {{ lesson.announcement || '공지사항이 작성되지 않았습니다.' }}
            </div>
          </div>
        </div>

        <!-- Edit Mode -->
        <div v-else>
          <el-form label-position="top">
            <el-form-item label="수업 공통 피드백">
              <el-input
                v-model="editCommonFeedback"
                type="textarea"
                :rows="6"
                placeholder="수업 공통 피드백을 입력하세요"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="공지사항">
              <el-input
                v-model="editAnnouncement"
                type="textarea"
                :rows="4"
                placeholder="공지사항을 입력하세요"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-card>

    <el-row :gutter="24">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <h3 style="margin: 0; font-size: 18px; font-weight: 600">시험 관리</h3>
              <el-button
                v-if="!lesson?.testTitle"
                type="primary"
                size="small"
                @click="openAttachTestDialog"
              >
                시험 연결
              </el-button>
            </div>
          </template>

          <div v-if="lesson?.testTitle">
            <div style="margin-bottom: 16px">
              <div style="font-size: 18px; font-weight: 600; margin-bottom: 8px">
                {{ lesson.testTitle }}
              </div>
              <el-tag type="success">연결됨</el-tag>
            </div>
            <div style="display: flex; gap: 12px">
              <el-button type="primary" @click="navigateToTestDetail(lesson.testId!)">
                시험 상세
              </el-button>
              <el-button type="warning" @click="handleDetachTest">
                시험 제거
              </el-button>
            </div>
          </div>
          <el-empty v-else description="연결된 시험이 없습니다" :image-size="100" />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <h3 style="margin: 0; font-size: 18px; font-weight: 600">숙제 관리</h3>
              <el-button
                type="primary"
                size="small"
                @click="openAddHomeworkDialog"
              >
                숙제 추가
              </el-button>
            </div>
          </template>

          <div v-if="lessonHomeworks.length > 0">
            <el-table :data="lessonHomeworks" style="width: 100%">
              <el-table-column prop="title" label="숙제 제목" min-width="200" />
              <el-table-column prop="questionCount" label="문제 수" width="100" align="center" />
              <el-table-column label="작업" width="100" align="center">
                <template #default="{ row }">
                  <el-button
                    type="danger"
                    size="small"
                    @click="handleRemoveHomework(row.id)"
                  >
                    제거
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-else description="등록된 숙제가 없습니다" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 출석 체크 섹션 -->
    <el-card shadow="never" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>출석 체크</span>
          <div>
            <el-button size="small" type="success" plain @click="setAllAttendance('PRESENT')">
              전체 출석
            </el-button>
            <el-button
              size="small"
              type="primary"
              :loading="attendanceSaving"
              @click="saveAttendance"
            >
              저장
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="attendanceList" stripe v-loading="attendanceLoading">
        <el-table-column prop="studentName" label="학생 이름" />
        <el-table-column label="출석 상태" width="180">
          <template #default="{ row }">
            <el-select
              v-model="row.attendanceStatus"
              placeholder="선택"
              clearable
              size="small"
            >
              <el-option
                v-for="(label, key) in attendanceLabels"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="상태" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.attendanceStatus"
              :color="attendanceColors[row.attendanceStatus]"
              effect="dark"
              size="small"
            >
              {{ attendanceLabels[row.attendanceStatus] }}
            </el-tag>
            <span v-else style="color: #999">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Student Test Scores Section -->
    <el-card v-if="lesson?.testTitle && stats?.testScores" shadow="never" style="margin-top: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0; font-size: 18px; font-weight: 600">학생별 시험 성적</h3>
          <el-tag type="primary" size="large">
            반 평균: {{ stats.testAverage?.toFixed(1) }}점
          </el-tag>
        </div>
      </template>

      <el-table :data="stats.testScores" style="width: 100%" stripe>
        <el-table-column label="등수" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rank === 1" type="danger" effect="dark">{{ row.rank }}등</el-tag>
            <el-tag v-else-if="row.rank === 2" type="warning" effect="dark">{{ row.rank }}등</el-tag>
            <el-tag v-else-if="row.rank === 3" type="success" effect="dark">{{ row.rank }}등</el-tag>
            <span v-else-if="row.rank" style="font-weight: 600">{{ row.rank }}등</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentName" label="학생 이름" min-width="150" />
        <el-table-column label="점수" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.submitted" style="font-size: 16px; font-weight: 600; color: #409eff">
              {{ row.score }}점
            </span>
            <el-tag v-else type="info">미제출</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Student Homework Assignment Section -->
    <el-card v-if="lessonHomeworks.length > 0 && studentAssignments.length > 0" shadow="never" style="margin-top: 24px">
      <template #header>
        <div>
          <h3 style="margin: 0 0 12px 0; font-size: 18px; font-weight: 600">학생별 숙제 할당</h3>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-select
              v-model="bulkAssignHomeworkId"
              placeholder="일괄 할당할 숙제 선택"
              style="width: 200px"
              size="small"
            >
              <el-option
                v-for="hw in lessonHomeworks"
                :key="hw.id"
                :label="hw.title"
                :value="hw.id"
              />
            </el-select>
            <el-button
              type="primary"
              size="small"
              @click="handleBulkAssign"
              :disabled="!bulkAssignHomeworkId"
            >
              일괄 할당
            </el-button>
            <el-button
              type="success"
              size="small"
              @click="saveAssignments"
            >
              할당 저장
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="studentAssignments" style="width: 100%" stripe>
        <el-table-column prop="studentName" label="학생 이름" min-width="150" />
        <el-table-column label="할당된 숙제" min-width="250" align="center">
          <template #default="{ row, $index }">
            <el-select
              v-model="studentAssignments[$index].assignedHomeworkId"
              placeholder="숙제를 선택하세요"
              style="width: 100%"
              clearable
              :disabled="row.incorrectCount !== null && row.incorrectCount !== undefined"
            >
              <el-option
                v-for="hw in lessonHomeworks"
                :key="hw.id"
                :label="hw.title"
                :value="hw.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="제출 상태" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.incorrectCount !== null && row.incorrectCount !== undefined" type="success">
              제출 완료
            </el-tag>
            <el-tag v-else-if="row.assignedHomeworkId" type="warning">
              미제출
            </el-tag>
            <el-tag v-else type="info">
              미할당
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        title="제출된 숙제는 다른 숙제로 변경할 수 없습니다"
        type="info"
        :closable="false"
        style="margin-top: 16px"
        show-icon
      />
    </el-card>

    <!-- Student Homework Completion Section -->
    <el-card v-if="lessonHomeworks.length > 0 && studentAssignments.length > 0" shadow="never" style="margin-top: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0; font-size: 18px; font-weight: 600">학생별 숙제 완성도</h3>
          <el-tag type="success" size="large">
            평균 완성도: {{ calculateAverageCompletion() }}%
          </el-tag>
        </div>
      </template>

      <el-table :data="studentAssignments.filter(s => s.assignedHomeworkId)" style="width: 100%" stripe>
        <el-table-column prop="studentName" label="학생" min-width="80" />
        <el-table-column label="오답 문항" min-width="200">
          <template #default="{ row }">
            <div v-if="editingHomeworkMap.get(row.studentId)">
              <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center">
                <el-tag
                  v-for="(tag, idx) in editIncorrectQuestionsMap.get(row.studentId) || []"
                  :key="tag"
                  closable
                  type="danger"
                  size="small"
                  @close="removeTag(editIncorrectQuestionsMap, editIncorrectCountMap, row.studentId, idx)"
                >
                  {{ tag }}번
                </el-tag>
                <el-input
                  :model-value="incorrectTagInput.get(row.studentId) || ''"
                  @update:model-value="(val: string) => incorrectTagInput.set(row.studentId, val)"
                  @keyup.enter="addTag(editIncorrectQuestionsMap, editIncorrectCountMap, row.studentId, incorrectTagInput)"
                  @keyup.space.prevent="addTag(editIncorrectQuestionsMap, editIncorrectCountMap, row.studentId, incorrectTagInput)"
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
            <div v-if="editingHomeworkMap.get(row.studentId)">
              <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center">
                <el-tag
                  v-for="(tag, idx) in editUnsolvedQuestionsMap.get(row.studentId) || []"
                  :key="tag"
                  closable
                  type="warning"
                  size="small"
                  @close="removeTag(editUnsolvedQuestionsMap, editUnsolvedCountMap, row.studentId, idx)"
                >
                  {{ tag }}번
                </el-tag>
                <el-input
                  :model-value="unsolvedTagInput.get(row.studentId) || ''"
                  @update:model-value="(val: string) => unsolvedTagInput.set(row.studentId, val)"
                  @keyup.enter="addTag(editUnsolvedQuestionsMap, editUnsolvedCountMap, row.studentId, unsolvedTagInput)"
                  @keyup.space.prevent="addTag(editUnsolvedQuestionsMap, editUnsolvedCountMap, row.studentId, unsolvedTagInput)"
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
        <el-table-column label="완성도" width="150" align="center">
          <template #default="{ row }">
            <div v-if="row.completion !== null && row.completion !== undefined || editingHomeworkMap.get(row.studentId)" style="display: flex; align-items: center; gap: 12px">
              <el-progress
                :percentage="row.completion || 0"
                :color="(row.completion || 0) >= 80 ? '#67c23a' : (row.completion || 0) >= 50 ? '#e6a23c' : '#f56c6c'"
                style="flex: 1"
              />
            </div>
            <el-tag v-else type="info">미제출</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="120" align="center">
          <template #default="{ row }">
            <div v-if="editingHomeworkMap.get(row.studentId)" style="display: flex; gap: 4px; justify-content: center">
              <el-button type="primary" size="small" @click="saveHomeworkIncorrectCount(row.studentId, row.assignedHomeworkId!, 100)">
                저장
              </el-button>
              <el-button size="small" @click="cancelEditingHomework(row.studentId)">
                취소
              </el-button>
            </div>
            <el-button v-else type="primary" size="small" @click="startEditingHomework(row.studentId, row)">
              편집
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="assignedHomeworkTitle" label="할당된 숙제" min-width="150" />
      </el-table>
    </el-card>

    <!-- Attach Test Dialog -->
    <el-dialog v-model="attachTestDialogVisible" title="시험 연결" width="500px">
      <el-form label-width="80px">
        <el-form-item label="시험 선택">
          <el-select v-model="selectedTestId" placeholder="시험을 선택하세요" style="width: 100%">
            <el-option
              v-for="test in unattachedTests"
              :key="test.id"
              :label="test.title"
              :value="test.id"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="unattachedTests.length === 0"
          title="연결 가능한 시험이 없습니다"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="attachTestDialogVisible = false">취소</el-button>
        <el-button
          type="primary"
          @click="handleAttachTest"
          :disabled="unattachedTests.length === 0"
        >
          연결
        </el-button>
      </template>
    </el-dialog>

    <!-- Add Homework Dialog -->
    <el-dialog v-model="addHomeworkDialogVisible" title="숙제 추가" width="500px">
      <el-form label-width="80px">
        <el-form-item label="숙제 선택">
          <el-select v-model="selectedHomeworkId" placeholder="숙제를 선택하세요" style="width: 100%">
            <el-option
              v-for="homework in unattachedHomeworks"
              :key="homework.id"
              :label="homework.title"
              :value="homework.id"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="unattachedHomeworks.length === 0"
          title="추가 가능한 숙제가 없습니다"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="addHomeworkDialogVisible = false">취소</el-button>
        <el-button
          type="primary"
          @click="handleAttachHomework"
          :disabled="unattachedHomeworks.length === 0"
        >
          추가
        </el-button>
      </template>
    </el-dialog>

    <!-- Video Management Section -->
    <el-card shadow="never" style="margin-top: 24px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h3 style="margin: 0; font-size: 18px; font-weight: 600">
            <el-icon style="margin-right: 8px"><VideoPlay /></el-icon>
            수업 영상
          </h3>
          <el-button type="primary" @click="addVideoDialogVisible = true">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>
            영상 추가
          </el-button>
        </div>
      </template>

      <div v-if="videos.length > 0">
        <el-card
          v-for="(video, index) in videos"
          :key="video.id"
          style="margin-bottom: 16px"
        >
          <div style="display: flex; gap: 16px; align-items: center">
            <img
              :src="video.thumbnailUrl"
              style="width: 160px; height: 90px; object-fit: cover; border-radius: 4px"
            />

            <div style="flex: 1">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
                <el-tag type="info" size="small">{{ index + 1 }}부</el-tag>
                <h4 style="margin: 0; font-size: 16px">{{ video.title }}</h4>
              </div>
              <p style="margin: 0; color: #909399; font-size: 14px">
                재생시간: {{ formatDuration(video.duration) }}
              </p>
            </div>

            <div style="display: flex; gap: 8px; align-items: center">
              <el-button
                size="small"
                type="success"
                @click="showVideoStats(video)"
              >
                <el-icon style="margin-right: 4px"><View /></el-icon>
                시청 현황
              </el-button>

              <el-tooltip content="위로 이동" placement="top">
                <el-button
                  size="small"
                  circle
                  @click="moveUp(video)"
                  :disabled="index === 0"
                >
                  <el-icon><Top /></el-icon>
                </el-button>
              </el-tooltip>

              <el-tooltip content="아래로 이동" placement="top">
                <el-button
                  size="small"
                  circle
                  @click="moveDown(video)"
                  :disabled="index === videos.length - 1"
                >
                  <el-icon><Bottom /></el-icon>
                </el-button>
              </el-tooltip>

              <el-tooltip content="삭제" placement="top">
                <el-button
                  size="small"
                  type="danger"
                  circle
                  @click="deleteVideo(video)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </el-card>
      </div>

      <el-empty v-else description="등록된 영상이 없습니다" />
    </el-card>

    <!-- Add Video Dialog -->
    <el-dialog
      v-model="addVideoDialogVisible"
      title="영상 추가"
      width="500px"
    >
      <el-form label-width="100px">
        <el-form-item label="YouTube URL" required>
          <el-input
            v-model="youtubeUrl"
            placeholder="https://www.youtube.com/watch?v=..."
            clearable
          />
          <div style="margin-top: 8px; font-size: 12px; color: #909399">
            YouTube 영상 URL을 입력하면 제목과 썸네일이 자동으로 가져와집니다
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addVideoDialogVisible = false">취소</el-button>
        <el-button
          type="primary"
          @click="addVideo"
          :disabled="!youtubeUrl"
        >
          추가
        </el-button>
      </template>
    </el-dialog>

    <!-- Video Stats Dialog -->
    <el-dialog
      v-model="videoStatsDialogVisible"
      :title="selectedVideoStats?.title + ' - 시청 현황'"
      width="800px"
    >
      <div v-if="selectedVideoStats">
        <el-table :data="selectedVideoStats.studentProgress" stripe style="width: 100%">
          <el-table-column prop="studentName" label="학생" width="120" />

          <el-table-column label="진행률" width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="row.progressPercent"
                :color="row.completed ? '#67c23a' : '#409eff'"
                :status="row.completed ? 'success' : undefined"
              />
            </template>
          </el-table-column>

          <el-table-column label="완료 여부" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.completed" type="success" size="small">완료</el-tag>
              <el-tag v-else type="info" size="small">시청 중</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="마지막 시청">
            <template #default="{ row }">
              <span v-if="row.lastWatchedAt" style="font-size: 13px; color: #909399">
                {{ new Date(row.lastWatchedAt).toLocaleString('ko-KR') }}
              </span>
              <span v-else style="font-size: 13px; color: #c0c4cc">시청 기록 없음</span>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="selectedVideoStats.studentProgress.length === 0" description="시청 기록이 없습니다" />
      </div>

      <template #footer>
        <el-button @click="videoStatsDialogVisible = false">닫기</el-button>
      </template>
    </el-dialog>
  </div>
</template>
