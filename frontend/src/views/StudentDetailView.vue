<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {
  type Student,
  studentAPI,
  type StudentHomework,
  studentHomeworkAPI,
  type Submission,
  submissionAPI,
  testAPI,
  authAPI,
  videoProgressAPI,
  type VideoProgress
} from '../api/client'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useTouchGestures } from '@/composables/useTouchGestures'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const currentUser = ref<any>({})
const studentId = ref<string>('')
const student = ref<Student | null>(null)
const submissions = ref<Submission[]>([])
const testStats = ref<Record<number, any>>({})
const studentHomeworks = ref<StudentHomework[]>([])
const videoProgress = ref<VideoProgress[]>([])
const activeTab = ref('test')

// 선생님인지 확인
const isTeacher = computed(() => currentUser.value.role === 'TEACHER')

// 모달 상태
const chartModalVisible = ref(false)
const incompleteHomeworksDialogVisible = ref(false)

// 반응형 breakpoint
const { isMobile, isSmallScreen } = useBreakpoint()

// Responsive font sizes
const h1FontSize = computed(() => isMobile.value ? '16px' : '28px')
const h2FontSize = computed(() => isMobile.value ? '14px' : '24px')
const h3FontSize = computed(() => isMobile.value ? '13px' : '18px')
const statFontSize = computed(() => isMobile.value ? '22px' : '32px')
const textFontSize = computed(() => isMobile.value ? '11px' : '15px')
const smallTextFontSize = computed(() => isMobile.value ? '9px' : '13px')
const bodyFontSize = computed(() => isMobile.value ? '11px' : '14px')
const cardPadding = computed(() => isMobile.value ? '16px' : '20px')
const iconSize = computed(() => isMobile.value ? 24 : 32)

// 터치 제스처 (차트용)
const {
  zoom: touchZoom,
  panX: touchPanX,
  panY: touchPanY,
  isDragging: isTouchDragging,
  handleTouchStart,
  handleTouchMove,
  handleTouchEnd,
  resetZoomAndPan: resetTouch
} = useTouchGestures()

// 확대/축소 및 이동 상태 (마우스용)
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)

// 통합 zoom/pan (마우스와 터치 제스처 통합)
const combinedZoom = computed(() => isDragging.value ? zoom.value : touchZoom.value)
const combinedPanX = computed(() => isDragging.value ? panX.value : touchPanX.value)
const combinedPanY = computed(() => isDragging.value ? panY.value : touchPanY.value)

// 반응형 스타일
const containerPadding = computed(() => isMobile.value ? '12px' : '24px')
const progressSize = computed(() => isMobile.value ? 80 : 120)
const progressStrokeWidth = computed(() => isMobile.value ? 8 : 10)
const chartViewBox = computed(() => isMobile.value ? '0 0 350 200' : '0 0 700 250')
const chartFontSize = computed(() => isMobile.value ? 10 : 12)
const dialogWidth = computed(() => isMobile.value ? '95%' : '90%')

// 차트 포인트 계산 (모든 좌표 사전 계산, 반응형)
const chartPoints = computed(() => {
  if (submissions.value.length === 0) return []

  // 반응형 차트 크기
  const chartWidth = isMobile.value ? 350 : 700
  const chartHeight = isMobile.value ? 200 : 250
  const marginLeft = isMobile.value ? 30 : 60
  const marginRight = isMobile.value ? 20 : 40
  const marginTop = isMobile.value ? 15 : 30
  const marginBottom = isMobile.value ? 15 : 30

  const points = submissions.value.map((s, index) => {
    const testId = s.testId
    const stats = testStats.value[testId]
    const studentScore = s.totalScore
    const academyAverage = stats?.averageScore || 0

    // X 좌표 계산
    const totalPoints = submissions.value.length
    const divisor = totalPoints > 1 ? totalPoints - 1 : 1
    const plotWidth = chartWidth - marginLeft - marginRight
    const x = marginLeft + (index * plotWidth / divisor)

    // Y 좌표 계산
    const plotHeight = chartHeight - marginTop - marginBottom
    const plotTop = marginTop
    const plotBottom = chartHeight - marginBottom
    const studentY = plotBottom - (studentScore / 100) * plotHeight
    const academyY = plotBottom - (academyAverage / 100) * plotHeight
    const labelY = studentY - 12

    return {
      date: s.submittedAt ? new Date(s.submittedAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }) : '',
      studentScore: studentScore,
      academyAverage: academyAverage,
      x: x,
      studentY: studentY,
      academyY: academyY,
      labelY: labelY
    }
  })

  return points
})

// 꺾은선 그래프 경로
const studentLinePoints = computed(() => {
  if (chartPoints.value.length < 2) return ''
  return chartPoints.value.map(p => `${p.x},${p.studentY}`).join(' ')
})

const academyLinePoints = computed(() => {
  if (chartPoints.value.length < 2) return ''
  return chartPoints.value.map(p => `${p.x},${p.academyY}`).join(' ')
})

// 평균 점수 계산
const averageScore = computed(() => {
  if (submissions.value.length === 0) return 0
  const total = submissions.value.reduce((sum, s) => sum + s.totalScore, 0)
  return Math.round(total / submissions.value.length * 10) / 10
})

// 최고 점수
const maxScore = computed(() => {
  if (submissions.value.length === 0) return 0
  return Math.max(...submissions.value.map(s => s.totalScore))
})

// 최저 점수
const minScore = computed(() => {
  if (submissions.value.length === 0) return 0
  return Math.min(...submissions.value.map(s => s.totalScore))
})

// incorrect_count가 null이 아닌 숙제만 필터링 (통계 계산용)
const duePastHomeworks = computed(() => {
  return studentHomeworks.value.filter(sh => sh.incorrectCount !== null && sh.incorrectCount !== undefined)
})

// 숙제 통계
const incompleteHomeworks = computed(() => {
  return duePastHomeworks.value.filter(sh => (sh.completion || 0) < 100)
})

const incompleteHomeworkCount = computed(() => {
  return incompleteHomeworks.value.length
})

const averageCompletion = computed(() => {
  if (duePastHomeworks.value.length === 0) return 0
  const total = duePastHomeworks.value.reduce((sum, sh) => sum + (sh.completion || 0), 0)
  return Math.round(total / duePastHomeworks.value.length * 10) / 10
})

const minCompletion = computed(() => {
  if (duePastHomeworks.value.length === 0) return 0
  return Math.min(...duePastHomeworks.value.map(sh => sh.completion || 0))
})

const totalIncorrectCount = computed(() => {
  return duePastHomeworks.value.reduce((sum, sh) => sum + (sh.incorrectCount || 0), 0)
})

// 완성도에 따른 색상 계산
const getCompletionColor = (percentage: number) => {
  if (percentage >= 90) return '#67c23a' // 초록 - 우수
  if (percentage >= 80) return '#95d475' // 연두 - 양호
  if (percentage >= 70) return '#e6a23c' // 노랑 - 보통
  if (percentage >= 60) return '#f39c12' // 주황 - 주의
  return '#f56c6c' // 빨강 - 미흡
}

const getCompletionBgColor = (percentage: number) => {
  if (percentage >= 90) return '#f0fdf4' // 초록 배경
  if (percentage >= 80) return '#f0fdf4' // 연두 배경
  if (percentage >= 70) return '#fffbeb' // 노랑 배경
  if (percentage >= 60) return '#fef3e2' // 주황 배경
  return '#fef2f2' // 빨강 배경
}

const averageCompletionColor = computed(() => getCompletionColor(averageCompletion.value))
const averageCompletionBgColor = computed(() => getCompletionBgColor(averageCompletion.value))
const minCompletionColor = computed(() => getCompletionColor(minCompletion.value))
const minCompletionBgColor = computed(() => getCompletionBgColor(minCompletion.value))

// 모달 열기
const openChartModal = () => {
  chartModalVisible.value = true
  resetZoomAndPan()
}

// 줌/팬 초기화 (마우스와 터치 모두 초기화)
const resetZoomAndPan = () => {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
  resetTouch()
}

// 마우스 휠로 줌
const handleWheel = (event: WheelEvent) => {
  event.preventDefault()
  const delta = event.deltaY > 0 ? 0.9 : 1.1
  const newZoom = Math.max(0.5, Math.min(5, zoom.value * delta))
  zoom.value = newZoom
}

// 드래그 시작
const handleMouseDown = (event: MouseEvent) => {
  isDragging.value = true
  dragStartX.value = event.clientX - panX.value
  dragStartY.value = event.clientY - panY.value
}

// 드래그 중
const handleMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) return
  panX.value = event.clientX - dragStartX.value
  panY.value = event.clientY - dragStartY.value
}

// 드래그 종료
const handleMouseUp = () => {
  isDragging.value = false
}

const fetchStudentDetail = async () => {
  loading.value = true
  try {
    // 현재 사용자 정보 가져오기
    const userRes = await authAPI.getCurrentUser()
    currentUser.value = userRes.data

    // 학생 ID 설정: STUDENT면 본인 ID, TEACHER면 route params ID
    if (currentUser.value.role === 'STUDENT') {
      studentId.value = String(currentUser.value.userId)
    } else {
      studentId.value = route.params.id as string
    }

    const [studentRes, submissionsRes, homeworksRes, videoProgressRes] = await Promise.all([
      studentAPI.getStudent(Number(studentId.value)),
      submissionAPI.getStudentSubmissions(Number(studentId.value)),
      studentHomeworkAPI.getByStudentId(Number(studentId.value)),
      videoProgressAPI.getStudentProgress(Number(studentId.value)).catch(() => ({ data: [] }))
    ])

    student.value = studentRes.data
    submissions.value = (submissionsRes.data.content || submissionsRes.data)
      .sort((a: Submission, b: Submission) => {
        const dateA = a.submittedAt ? new Date(a.submittedAt).getTime() : 0
        const dateB = b.submittedAt ? new Date(b.submittedAt).getTime() : 0
        return dateA - dateB
      })
    studentHomeworks.value = homeworksRes.data.content || homeworksRes.data
    videoProgress.value = videoProgressRes.data

    // 각 시험의 통계 가져오기
    const testIds = [...new Set(submissions.value.map(s => s.testId).filter(id => id))]
    const statsPromises = testIds.map(async (testId) => {
      try {
        const response = await testAPI.getTestStats(testId)
        testStats.value[testId] = response.data
      } catch (error) {
        console.error(`Failed to fetch stats for test ${testId}`)
      }
    })
    await Promise.all(statsPromises)
  } catch (error) {
    ElMessage.error('학생 정보를 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  if (isTeacher.value) {
    router.push('/students')
  } else {
    router.push('/student/dashboard')
  }
}

const navigateToTest = (testId: number | undefined) => {
  if (!testId) {
    ElMessage.warning('시험 정보를 찾을 수 없습니다')
    return
  }
  router.push(`/tests/${testId}`)
}

onMounted(() => {
  fetchStudentDetail()
})
</script>

<template>
  <div :class="{ 'student-view': !isTeacher, 'teacher-view': isTeacher }" v-loading="loading" :style="{ padding: containerPadding, maxWidth: '1200px', margin: '0 auto' }">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div>
        <h1 :style="{ margin: isMobile ? '0 0 12px 0' : '0 0 16px 0', fontSize: h1FontSize, fontWeight: 600, color: '#303133', display: 'flex', alignItems: 'center', gap: isMobile ? '8px' : '12px' }">
          <el-icon :size="iconSize" color="#409eff">
            <User />
          </el-icon>
          <span>{{ student?.name || '학생 상세' }}</span>
        </h1>
        <div :style="{ display: 'flex', gap: isMobile ? '8px' : '12px', flexWrap: 'wrap', alignContent: 'flex-start', justifyContent: 'flex-start', margin: 0, width: '100%' }">
          <el-button type="primary" @click="router.push(isTeacher ? `/students/${studentId}/feedback` : '/student/daily-feedback')" :size="isMobile ? 'small' : 'default'" style="margin: 0">
            <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><ChatLineSquare /></el-icon>
            수업 피드백
          </el-button>
          <el-button @click="goBack" :icon="ArrowLeft" :size="isMobile ? 'small' : 'default'" style="margin: 0">
            {{ isTeacher ? '목록으로' : '대시보드로' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Student Info & Stats -->
    <el-row :gutter="24" style="margin-bottom: 24px">
      <!-- 학생 정보 -->
      <el-col :xs="24" :sm="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#409eff">
                <InfoFilled />
              </el-icon>
              <span style="font-weight: 600">학생 정보</span>
            </div>
          </template>

          <div style="text-align: center; padding: 20px 0">
            <el-avatar :size="80" :icon="UserFilled" style="margin-bottom: 16px" />
            <h2 :style="{ margin: '0 0 8px', fontSize: h2FontSize, fontWeight: 600 }">{{ student?.name }}</h2>
            <el-tag type="info" size="small">ID: {{ student?.id }}</el-tag>

            <div style="margin-top: 24px; text-align: left">
              <div style="padding: 12px 0; border-bottom: 1px solid #ebeef5">
                <span :style="{ color: '#909399', fontSize: bodyFontSize }">학년</span>
                <div style="margin-top: 4px; font-weight: 500">{{ student?.grade }}</div>
              </div>
              <div style="padding: 12px 0; border-bottom: 1px solid #ebeef5">
                <span :style="{ color: '#909399', fontSize: bodyFontSize }">학교</span>
                <div style="margin-top: 4px; font-weight: 500">{{ student?.school }}</div>
              </div>
              <div style="padding: 12px 0; border-bottom: 1px solid #ebeef5">
                <span :style="{ color: '#909399', fontSize: bodyFontSize }">학원</span>
                <div style="margin-top: 4px; font-weight: 500">{{ student?.academyName }}</div>
              </div>
              <div style="padding: 12px 0">
                <span :style="{ color: '#909399', fontSize: bodyFontSize }">반</span>
                <div style="margin-top: 4px; font-weight: 500">{{ student?.className }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 통계 -->
      <el-col :xs="24" :sm="24" :md="16">
        <el-card shadow="never">
          <el-tabs v-model="activeTab" type="card">
            <!-- 시험 통계 탭 -->
            <el-tab-pane label="시험 통계" name="test">
              <el-row :gutter="16" style="margin-bottom: 16px">
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #f0f9ff; border-radius: 8px">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#409eff', marginBottom: '8px' }">
                      {{ submissions.length }}
                    </div>
                    <div :style="{ color: '#909399', fontSize: bodyFontSize }">총 응시 시험</div>
                  </div>
                </el-col>
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #f0fdf4; border-radius: 8px">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#67c23a', marginBottom: '8px' }">
                      {{ averageScore }}
                    </div>
                    <div :style="{ color: '#909399', fontSize: bodyFontSize }">평균 점수</div>
                  </div>
                </el-col>
              </el-row>

              <el-row :gutter="16">
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #fffbeb; border-radius: 8px">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#e6a23c', marginBottom: '8px' }">
                      {{ maxScore }}
                    </div>
                    <div :style="{ color: '#909399', fontSize: bodyFontSize }">최고 점수</div>
                  </div>
                </el-col>
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #fef2f2; border-radius: 8px">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#f56c6c', marginBottom: '8px' }">
                      {{ minScore }}
                    </div>
                    <div :style="{ color: '#909399', fontSize: bodyFontSize }">최저 점수</div>
                  </div>
                </el-col>
              </el-row>

          <!-- 꺾은선 그래프 -->
          <div v-if="chartPoints && chartPoints.length > 0" style="margin-top: 24px; padding: 20px; background: #fafafa; border-radius: 8px; cursor: pointer; transition: all 0.3s" @click="openChartModal">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px">
              <div :style="{ fontWeight: 600, color: '#303133', fontSize: textFontSize }">점수 추이</div>
              <div :style="{ display: 'flex', gap: '12px', fontSize: smallTextFontSize, alignItems: 'center', flexWrap: 'nowrap' }">
                <div style="display: flex; align-items: center; gap: 4px; white-space: nowrap">
                  <div style="width: 14px; height: 2.5px; background: #409eff; border-radius: 2px"></div>
                  <span style="color: #606266">내 점수</span>
                </div>
                <div style="display: flex; align-items: center; gap: 4px; white-space: nowrap">
                  <div style="width: 14px; height: 2.5px; background: #67c23a; border-radius: 2px"></div>
                  <span style="color: #606266">학원 평균</span>
                </div>
                <el-button type="primary" size="small" :icon="ZoomIn" @click.stop="openChartModal" :style="{ padding: '4px 8px', fontSize: smallTextFontSize }">
                  확대하여 보기
                </el-button>
              </div>
            </div>

            <svg :viewBox="chartViewBox" style="width: 100%; height: auto">
              <!-- 배경 격자선 -->
              <line x1="60" y1="30" x2="680" y2="30" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
              <line x1="60" y1="70" x2="680" y2="70" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
              <line x1="60" y1="110" x2="680" y2="110" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
              <line x1="60" y1="150" x2="680" y2="150" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
              <line x1="60" y1="190" x2="680" y2="190" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />

              <!-- Y축 레이블 -->
              <text x="50" y="35" text-anchor="end" font-size="12" fill="#909399">100</text>
              <text x="50" y="75" text-anchor="end" font-size="12" fill="#909399">75</text>
              <text x="50" y="115" text-anchor="end" font-size="12" fill="#909399">50</text>
              <text x="50" y="155" text-anchor="end" font-size="12" fill="#909399">25</text>
              <text x="50" y="195" text-anchor="end" font-size="12" fill="#909399">0</text>

              <!-- X축 -->
              <line x1="60" y1="190" x2="680" y2="190" stroke="#303133" stroke-width="2" />

              <!-- 학원 평균 선 -->
              <polyline
                v-if="chartPoints.length > 1"
                :points="academyLinePoints"
                fill="none" stroke="#67c23a" stroke-width="2.5"
              />

              <!-- 내 점수 선 -->
              <polyline
                v-if="chartPoints.length > 1"
                :points="studentLinePoints"
                fill="none" stroke="#409eff" stroke-width="3"
              />

              <!-- 데이터 포인트 및 레이블 -->
              <template v-for="(point, index) in chartPoints" :key="index">
                <!-- X축 레이블 -->
                <text
                  :x="point.x"
                  y="210"
                  text-anchor="middle"
                  font-size="11"
                  fill="#606266"
                >
                  {{ point.date }}
                </text>

                <!-- 학원 평균 점 -->
                <circle
                  :cx="point.x"
                  :cy="point.academyY"
                  r="5"
                  fill="#67c23a"
                  stroke="white"
                  stroke-width="2"
                />

                <!-- 내 점수 점 -->
                <circle
                  :cx="point.x"
                  :cy="point.studentY"
                  r="6"
                  fill="#409eff"
                  stroke="white"
                  stroke-width="2"
                >
                  <title>{{ point.studentScore }}점 (평균: {{ point.academyAverage }}점)</title>
                </circle>

                <!-- 점수 레이블 -->
                <text
                  :x="point.x"
                  :y="point.labelY"
                  text-anchor="middle"
                  font-size="12"
                  font-weight="600"
                  fill="#409eff"
                >
                  {{ point.studentScore }}
                </text>
              </template>
            </svg>
          </div>

          <el-empty v-else description="아직 응시한 시험이 없습니다" />
            </el-tab-pane>

            <!-- 숙제 통계 탭 -->
            <el-tab-pane label="숙제 통계" name="homework">
              <el-row :gutter="16" style="margin-bottom: 16px">
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #fef2f2; border-radius: 8px; position: relative">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#f56c6c', marginBottom: '8px' }">
                      {{ incompleteHomeworkCount }}
                    </div>
                    <div
                      :style="{ color: '#409eff', fontSize: bodyFontSize, cursor: 'pointer', textDecoration: 'underline', transition: 'color 0.3s' }"
                      @click="incompleteHomeworksDialogVisible = true"
                      @mouseenter="e => e.currentTarget.style.color = '#66b1ff'"
                      @mouseleave="e => e.currentTarget.style.color = '#409eff'"
                    >
                      미완성 숙제 수
                    </div>
                  </div>
                </el-col>
                <el-col :xs="12" :sm="12" :md="12">
                  <div style="text-align: center; padding: 20px; background: #fff7e6; border-radius: 8px">
                    <div :style="{ fontSize: statFontSize, fontWeight: 600, color: '#e6a23c', marginBottom: '8px' }">
                      {{ totalIncorrectCount }}개
                    </div>
                    <div :style="{ color: '#909399', fontSize: bodyFontSize }">총 오답 개수</div>
                  </div>
                </el-col>
              </el-row>

              <el-row :gutter="16">
                <el-col :xs="12" :sm="12" :md="12">
                  <div :style="{ padding: '10px', background: averageCompletionBgColor, borderRadius: '8px', textAlign: 'center', transition: 'background 0.3s' }">
                    <div :style="{ color: '#909399', fontSize: bodyFontSize, marginBottom: '12px' }">평균 완성도</div>
                    <el-progress
                      type="circle"
                      :percentage="averageCompletion"
                      :color="averageCompletionColor"
                      :width="progressSize"
                      :stroke-width="progressStrokeWidth"
                    />
                  </div>
                </el-col>
                <el-col :xs="12" :sm="12" :md="12">
                  <div :style="{ padding: '10px', background: minCompletionBgColor, borderRadius: '8px', textAlign: 'center', transition: 'background 0.3s' }">
                    <div :style="{ color: '#909399', fontSize: bodyFontSize, marginBottom: '12px' }">최저 완성도</div>
                    <el-progress
                      type="circle"
                      :percentage="minCompletion"
                      :color="minCompletionColor"
                      :width="progressSize"
                      :stroke-width="progressStrokeWidth"
                    />
                  </div>
                </el-col>
              </el-row>

              <!-- 숙제 목록 -->
              <div v-if="studentHomeworks.length > 0" style="margin-top: 24px">
                <el-table :data="studentHomeworks" style="width: 100%">
                  <el-table-column prop="homeworkTitle" label="숙제명" min-width="200" />
                  <el-table-column label="오답 개수" width="120" align="center">
                    <template #default="{ row }">
                      <el-tag :type="(row.incorrectCount || 0) === 0 ? 'success' : 'warning'">
                        {{ row.incorrectCount || 0 }}개
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="완성도" width="150" align="center">
                    <template #default="{ row }">
                      <el-progress
                        :percentage="row.completion || 0"
                        :color="row.completion >= 80 ? '#67c23a' : row.completion >= 60 ? '#e6a23c' : '#f56c6c'"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="제출 기한" width="150">
                    <template #default="{ row }">
                      <span v-if="row.dueDate">{{ new Date(row.dueDate).toLocaleDateString('ko-KR') }}</span>
                      <span v-else style="color: #909399">-</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <el-empty v-else description="아직 완성한 숙제가 없습니다" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- 시험 목록 -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon color="#67c23a">
            <Document />
          </el-icon>
          <span style="font-weight: 600">응시한 시험</span>
        </div>
      </template>

      <!-- Desktop: Table View -->
      <el-table v-if="!isMobile" :data="submissions" style="width: 100%" stripe>
        <el-table-column label="시험명" min-width="200">
          <template #default="{ row }">
            <div
              style="cursor: pointer; color: #409eff; font-weight: 500"
              @click="navigateToTest(row.testId)"
            >
              <el-icon style="margin-right: 4px"><Document /></el-icon>
              {{ row.testTitle || '시험명 없음' }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="점수" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.totalScore >= 80 ? 'success' : row.totalScore >= 60 ? 'warning' : 'danger'"
              size="large"
            >
              {{ row.totalScore }}점
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="응시일" width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#909399">
                <Calendar />
              </el-icon>
              {{ row.submittedAt ? new Date(row.submittedAt).toLocaleString('ko-KR') : '-' }}
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile: Card View -->
      <div v-else>
        <el-card
          v-for="submission in submissions"
          :key="submission.id"
          shadow="hover"
          style="margin-bottom: 16px; cursor: pointer"
          @click="navigateToTest(submission.testId)"
        >
          <div style="display: flex; flex-direction: column; gap: 12px">
            <!-- Title and Score -->
            <div style="display: flex; justify-content: space-between; align-items: start; gap: 12px">
              <div style="flex: 1">
                <h3 :style="{ margin: 0, fontSize: h3FontSize, fontWeight: 600, color: '#409eff' }">
                  {{ submission.testTitle || '시험명 없음' }}
                </h3>
              </div>
              <el-tag
                :type="submission.totalScore >= 80 ? 'success' : submission.totalScore >= 60 ? 'warning' : 'danger'"
                size="large"
              >
                {{ submission.totalScore }}점
              </el-tag>
            </div>

            <!-- Date Info -->
            <div :style="{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: bodyFontSize, color: '#606266' }">
              <el-icon color="#909399">
                <Calendar />
              </el-icon>
              <span>{{ submission.submittedAt ? new Date(submission.submittedAt).toLocaleString('ko-KR', {
                year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
              }) : '-' }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <el-empty v-if="submissions.length === 0" description="아직 응시한 시험이 없습니다" />
    </el-card>

    <!-- 영상 시청 현황 -->
    <el-card v-if="videoProgress.length > 0" shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon color="#e6a23c">
            <VideoPlay />
          </el-icon>
          <span style="font-weight: 600">영상 시청 현황</span>
        </div>
      </template>

      <el-table v-if="!isMobile" :data="videoProgress" style="width: 100%" stripe>
        <el-table-column label="영상 제목" min-width="200">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon color="#e6a23c"><VideoPlay /></el-icon>
              <span style="font-weight: 500">영상 {{ row.videoId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="진행률" width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progressPercent"
              :color="row.completed ? '#67c23a' : '#409eff'"
              :status="row.completed ? 'success' : undefined"
            />
          </template>
        </el-table-column>

        <el-table-column label="완료 여부" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.completed" type="success" size="small">완료</el-tag>
            <el-tag v-else type="info" size="small">시청 중</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="마지막 시청" width="180">
          <template #default="{ row }">
            <span v-if="row.lastWatchedAt" style="font-size: 13px; color: #909399">
              {{ new Date(row.lastWatchedAt).toLocaleString('ko-KR') }}
            </span>
            <span v-else style="font-size: 13px; color: #c0c4cc">-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile View -->
      <div v-if="isMobile">
        <el-card
          v-for="progress in videoProgress"
          :key="progress.videoId"
          :style="{ marginBottom: '12px', padding: cardPadding }"
          shadow="hover"
        >
          <div style="display: flex; flex-direction: column; gap: 12px">
            <div style="display: flex; justify-content: space-between; align-items: center">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#e6a23c"><VideoPlay /></el-icon>
                <span style="font-weight: 600">영상 {{ progress.videoId }}</span>
              </div>
              <el-tag v-if="progress.completed" type="success" size="small">완료</el-tag>
              <el-tag v-else type="info" size="small">시청 중</el-tag>
            </div>

            <el-progress
              :percentage="progress.progressPercent"
              :color="progress.completed ? '#67c23a' : '#409eff'"
              :status="progress.completed ? 'success' : undefined"
            />

            <div v-if="progress.lastWatchedAt" :style="{ fontSize: smallTextFontSize, color: '#909399' }">
              마지막 시청: {{ new Date(progress.lastWatchedAt).toLocaleString('ko-KR') }}
            </div>
          </div>
        </el-card>
      </div>
    </el-card>

    <!-- 차트 확대 모달 -->
    <el-dialog
      v-model="chartModalVisible"
      title="점수 추이 상세"
      :width="dialogWidth"
      :fullscreen="isMobile"
      top="5vh"
      :close-on-click-modal="false"
    >
      <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
        <div :style="{ display: 'flex', gap: '16px', fontSize: bodyFontSize }">
          <div style="display: flex; align-items: center; gap: 8px">
            <div style="width: 20px; height: 4px; background: #409eff; border-radius: 2px"></div>
            <span style="color: #606266">내 점수</span>
          </div>
          <div style="display: flex; align-items: center; gap: 8px">
            <div style="width: 20px; height: 4px; background: #67c23a; border-radius: 2px"></div>
            <span style="color: #606266">학원 평균</span>
          </div>
        </div>
        <div style="display: flex; gap: 12px; align-items: center">
          <span :style="{ color: '#909399', fontSize: smallTextFontSize }">확대: {{ Math.round(combinedZoom * 100) }}%</span>
          <el-button size="small" @click="resetZoomAndPan" :icon="Refresh">초기화</el-button>
        </div>
      </div>

      <div
        style="
          background: #fafafa;
          border-radius: 8px;
          overflow: hidden;
          position: relative;
          height: 600px;
          touch-action: none;
        "
        @wheel="handleWheel"
        @mousedown="handleMouseDown"
        @mousemove="handleMouseMove"
        @mouseup="handleMouseUp"
        @mouseleave="handleMouseUp"
        @touchstart="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend="handleTouchEnd"
        @touchcancel="handleTouchEnd"
      >
        <svg
          :viewBox="chartViewBox"
          style="width: 100%; height: 100%; cursor: grab"
          :style="{ cursor: isDragging || isTouchDragging ? 'grabbing' : 'grab' }"
        >
          <g :transform="`translate(${combinedPanX}, ${combinedPanY}) scale(${combinedZoom})`" style="transform-origin: center">
            <!-- 배경 격자선 -->
            <line x1="60" y1="30" x2="680" y2="30" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
            <line x1="60" y1="70" x2="680" y2="70" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
            <line x1="60" y1="110" x2="680" y2="110" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
            <line x1="60" y1="150" x2="680" y2="150" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />
            <line x1="60" y1="190" x2="680" y2="190" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4" />

            <!-- Y축 레이블 -->
            <text x="50" y="35" text-anchor="end" font-size="12" fill="#909399">100</text>
            <text x="50" y="75" text-anchor="end" font-size="12" fill="#909399">75</text>
            <text x="50" y="115" text-anchor="end" font-size="12" fill="#909399">50</text>
            <text x="50" y="155" text-anchor="end" font-size="12" fill="#909399">25</text>
            <text x="50" y="195" text-anchor="end" font-size="12" fill="#909399">0</text>

            <!-- X축 -->
            <line x1="60" y1="190" x2="680" y2="190" stroke="#303133" stroke-width="2" />

            <!-- 학원 평균 선 -->
            <polyline
              v-if="chartPoints.length > 1"
              :points="academyLinePoints"
              fill="none" stroke="#67c23a" stroke-width="2.5"
            />

            <!-- 내 점수 선 -->
            <polyline
              v-if="chartPoints.length > 1"
              :points="studentLinePoints"
              fill="none" stroke="#409eff" stroke-width="3"
            />

            <!-- 데이터 포인트 및 레이블 -->
            <template v-for="(point, index) in chartPoints" :key="index">
              <!-- X축 레이블 -->
              <text
                :x="point.x"
                y="210"
                text-anchor="middle"
                font-size="12"
                fill="#606266"
              >
                {{ point.date }}
              </text>

              <!-- 학원 평균 점 -->
              <circle
                :cx="point.x"
                :cy="point.academyY"
                r="6"
                fill="#67c23a"
                stroke="white"
                stroke-width="2"
              />

              <!-- 내 점수 점 -->
              <circle
                :cx="point.x"
                :cy="point.studentY"
                r="7"
                fill="#409eff"
                stroke="white"
                stroke-width="2"
              >
                <title>{{ point.studentScore }}점 (평균: {{ point.academyAverage }}점)</title>
              </circle>

              <!-- 점수 레이블 -->
              <text
                :x="point.x"
                :y="point.labelY"
                text-anchor="middle"
                font-size="14"
                font-weight="600"
                fill="#409eff"
              >
                {{ point.studentScore }}
              </text>

              <!-- 학원 평균 레이블 -->
              <text
                :x="point.x"
                :y="point.academyY + 20"
                text-anchor="middle"
                font-size="12"
                font-weight="500"
                fill="#67c23a"
              >
                {{ Math.round(point.academyAverage) }}
              </text>
            </template>
          </g>
        </svg>
      </div>

      <div :style="{ marginTop: '16px', padding: '12px', background: '#f0f9ff', borderRadius: '8px', fontSize: smallTextFontSize, color: '#606266' }">
        <div style="font-weight: 600; margin-bottom: 8px; color: #409eff">사용 방법</div>
        <div v-if="isMobile">• 핀치: 확대/축소</div>
        <div v-if="isMobile">• 드래그: 차트 이동</div>
        <div v-if="!isMobile">• 마우스 휠: 확대/축소</div>
        <div v-if="!isMobile">• 클릭 & 드래그: 차트 이동</div>
        <div>• 초기화 버튼: 원래 크기로 복원</div>
      </div>
    </el-dialog>

    <!-- 미완성 숙제 목록 다이얼로그 -->
    <el-dialog
      v-model="incompleteHomeworksDialogVisible"
      title="미완성 숙제 목록"
      :width="isMobile ? '95%' : '700px'"
      :fullscreen="isMobile"
    >
      <div v-if="incompleteHomeworks.length > 0">
        <el-table :data="incompleteHomeworks" style="width: 100%">
          <el-table-column prop="homeworkTitle" label="숙제명" min-width="200" />
          <el-table-column label="오답 개수" width="120" align="center">
            <template #default="{ row }">
              <el-tag type="warning">{{ row.incorrectCount || 0 }}개</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="완성도" width="150" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="row.completion || 0"
                :color="row.completion >= 80 ? '#67c23a' : row.completion >= 60 ? '#e6a23c' : '#f56c6c'"
              />
            </template>
          </el-table-column>
          <el-table-column label="제출 기한" width="120">
            <template #default="{ row }">
              <span v-if="row.dueDate">{{ new Date(row.dueDate).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }) }}</span>
              <span v-else style="color: #909399">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="모든 숙제를 완료했습니다!" />
      <template #footer>
        <el-button @click="incompleteHomeworksDialogVisible = false">닫기</el-button>
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

/* 차트 호버 효과 */
div[style*="cursor: pointer"]:hover {
  background: #f0f9ff !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
</style>
