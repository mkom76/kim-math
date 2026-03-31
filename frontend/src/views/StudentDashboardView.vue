<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI, lessonAPI, submissionAPI, studentAPI } from '@/api/client'
import type { AuthResponse, Test, Submission, Student, Lesson, AttendanceStats } from '@/api/client'
import { useBreakpoint } from '@/composables/useBreakpoint'

const router = useRouter()
const loading = ref(false)
const currentUser = ref<AuthResponse>({})
const studentInfo = ref<Student | null>(null)
const availableTests = ref<Test[]>([])
const mySubmissions = ref<Submission[]>([])
const pastTestsDialogVisible = ref(false)
const attendanceDialogVisible = ref(false)
const attendanceStats = ref<AttendanceStats | null>(null)

const { isMobile } = useBreakpoint()
const containerPadding = computed(() => isMobile.value ? '10px' : '24px')
const cardPadding = computed(() => isMobile.value ? '12px' : '20px')
const iconSize = computed(() => isMobile.value ? 24 : 32)
const statIconSize = computed(() => isMobile.value ? 38 : 48)
const h1FontSize = computed(() => isMobile.value ? '16px' : '28px')
const h2FontSize = computed(() => isMobile.value ? '14px' : '20px')
const h3FontSize = computed(() => isMobile.value ? '13px' : '18px')
const statFontSize = computed(() => isMobile.value ? '22px' : '32px')
const bodyFontSize = computed(() => isMobile.value ? '11px' : '14px')
const tableFontSize = computed(() => isMobile.value ? '10px' : '14px')

const fetchCurrentUser = async () => {
  try {
    const response = await authAPI.getCurrentUser()
    currentUser.value = response.data

    if (currentUser.value.userId) {
      // Fetch student info
      const studentResponse = await studentAPI.getStudent(currentUser.value.userId)
      studentInfo.value = studentResponse.data

      // Fetch lessons for student's class (lesson-based filtering)
      const lessonsResponse = await lessonAPI.getLessonsByStudent(currentUser.value.userId)
      const lessons: Lesson[] = lessonsResponse.data

      // Extract tests from lessons that have tests attached
      availableTests.value = lessons
        .filter(lesson => lesson.testId && lesson.testTitle)
        .map(lesson => ({
          id: lesson.testId,
          title: lesson.testTitle,
          className: lesson.className,
          academyName: lesson.academyName,
          // Add lesson date info for display
          lessonDate: lesson.lessonDate
        } as Test))

      // Fetch student's submissions
      const submissionsResponse = await submissionAPI.getStudentSubmissions(currentUser.value.userId)
      mySubmissions.value = submissionsResponse.data

      // Fetch attendance stats
      try {
        attendanceStats.value = await lessonAPI.getAttendanceStats(currentUser.value.userId)
      } catch {
        // 실패 시 무시
      }
    }
  } catch (error) {
    console.error('Failed to fetch user data:', error)
    ElMessage.error('사용자 정보를 불러오는데 실패했습니다')
  }
}

const handleLogout = async () => {
  try {
    await authAPI.logout()
    ElMessage.success('로그아웃 되었습니다')
    router.push('/login')
  } catch (error) {
    console.error('Logout failed:', error)
    ElMessage.error('로그아웃에 실패했습니다')
  }
}

const getSubmissionForTest = (testId: number) => {
  return mySubmissions.value.find(s => s.testId === testId)
}

const handleTakeTest = (testId: number) => {
  router.push(`/student/tests/${testId}`)
}

const showPastTestsDialog = () => {
  pastTestsDialogVisible.value = true
}

// 미응시한 시험 개수
const untakenTestsCount = () => {
  const submittedTestIds = new Set(mySubmissions.value.map(s => s.testId))
  return availableTests.value.filter(t => !submittedTestIds.has(t.id)).length
}

onMounted(() => {
  fetchCurrentUser()
})
</script>

<template>
  <div class="student-view" :style="{ padding: containerPadding, maxWidth: '1200px', margin: '0 auto' }">
    <!-- Top Right Actions -->
    <div :style="{ display: 'flex', justifyContent: 'flex-end', gap: isMobile ? '8px' : '12px', marginBottom: isMobile ? '12px' : '16px' }">
      <el-button @click="$router.push('/settings')" :size="isMobile ? 'small' : 'default'">
        <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><Setting /></el-icon>
        설정
      </el-button>
      <el-button type="danger" @click="handleLogout" :size="isMobile ? 'small' : 'default'">
        로그아웃
      </el-button>
    </div>

    <!-- Header -->
    <el-card shadow="never" :style="{ marginBottom: isMobile ? '16px' : '24px' }">
      <div>
        <div :style="{ marginBottom: isMobile ? '12px' : '16px' }">
          <h1 :style="{ margin: 0, fontSize: h1FontSize, fontWeight: 600, color: '#303133', display: 'flex', alignItems: 'center', gap: isMobile ? '8px' : '12px' }">
            <el-icon :size="iconSize" color="#409eff">
              <UserFilled />
            </el-icon>
            <span>{{ currentUser.name }}님</span>
          </h1>
          <p v-if="studentInfo" :style="{ margin: '8px 0 0 0', color: '#909399', fontSize: bodyFontSize }">
            {{ studentInfo.academyName }} · {{ studentInfo.className }}
          </p>
        </div>
        <div :style="{ display: 'flex', flexWrap: 'wrap', gap: isMobile ? '8px' : '12px', width: '100%' }">
          <el-button type="success" @click="$router.push(`/student/stats`)" :size="isMobile ? 'small' : 'default'" :style="{ margin: 0, flex: '1 1 calc(25% - 12px)', minWidth: isMobile ? 'calc(50% - 4px)' : '120px' }">
            <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><TrendCharts /></el-icon>
            내 학습 통계
          </el-button>
          <el-button type="primary" @click="$router.push('/student/daily-feedback')" :size="isMobile ? 'small' : 'default'" :style="{ margin: 0, flex: '1 1 calc(25% - 12px)', minWidth: isMobile ? 'calc(50% - 4px)' : '120px' }">
            <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><Document /></el-icon>
            수업 피드백
          </el-button>
          <el-button type="warning" @click="$router.push('/student/clinic')" :size="isMobile ? 'small' : 'default'" :style="{ margin: 0, flex: '1 1 calc(25% - 12px)', minWidth: isMobile ? 'calc(50% - 4px)' : '120px' }">
            <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><MagicStick /></el-icon>
            클리닉 관리
          </el-button>
          <el-button type="info" @click="$router.push('/student/videos')" :size="isMobile ? 'small' : 'default'" :style="{ margin: 0, flex: '1 1 calc(25% - 12px)', minWidth: isMobile ? 'calc(50% - 4px)' : '120px' }">
            <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><VideoPlay /></el-icon>
            수업 다시보기
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Student Info Card -->
    <el-row :gutter="isMobile ? 12 : 24" :style="{ marginBottom: isMobile ? '12px' : '24px' }">
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" :body-style="{ padding: isMobile ? '10px' : '20px', height: isMobile ? '100px' : '120px', display: 'flex', alignItems: 'center', justifyContent: 'center' }">
          <div style="text-align: center">
            <h3 :style="{ margin: 0, fontSize: h3FontSize, color: '#409eff', marginBottom: isMobile ? '4px' : '8px' }">내 정보</h3>
            <p :style="{ margin: isMobile ? '2px 0' : '4px 0', color: '#606266', fontSize: bodyFontSize }">{{ studentInfo?.school || '-' }}</p>
            <p :style="{ margin: 0, color: '#909399', fontSize: bodyFontSize }">{{ studentInfo?.grade || '-' }}</p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" :body-style="{ padding: isMobile ? '10px' : '20px', height: isMobile ? '100px' : '120px', display: 'flex', alignItems: 'center', justifyContent: 'center' }">
          <div style="text-align: center">
            <h3 :style="{ margin: 0, fontSize: h3FontSize, color: '#67c23a', marginBottom: isMobile ? '4px' : '8px' }">미응시</h3>
            <p :style="{ margin: 0, color: '#606266', fontSize: statFontSize, fontWeight: 700 }">
              {{ untakenTestsCount() }}
            </p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" style="cursor: pointer" :body-style="{ padding: isMobile ? '10px' : '20px', height: isMobile ? '100px' : '120px', display: 'flex', alignItems: 'center', justifyContent: 'center' }" @click="showPastTestsDialog">
          <div style="text-align: center">
            <h3 :style="{ margin: 0, fontSize: h3FontSize, color: '#e6a23c', marginBottom: isMobile ? '4px' : '8px' }">지난 시험</h3>
            <p :style="{ margin: 0, color: '#606266', fontSize: statFontSize, fontWeight: 700 }">
              {{ mySubmissions.length }}
            </p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" style="cursor: pointer" :body-style="{ padding: isMobile ? '10px' : '20px', height: isMobile ? '100px' : '120px', display: 'flex', alignItems: 'center', justifyContent: 'center' }" @click="attendanceDialogVisible = true">
          <div style="text-align: center">
            <h3 :style="{ margin: 0, fontSize: h3FontSize, marginBottom: isMobile ? '4px' : '8px', color: attendanceStats && attendanceStats.attendanceRate >= 80 ? '#67C23A' : '#F56C6C' }">출석률</h3>
            <p :style="{ margin: 0, color: attendanceStats && attendanceStats.attendanceRate >= 80 ? '#67C23A' : '#F56C6C', fontSize: statFontSize, fontWeight: 700 }">
              {{ attendanceStats ? attendanceStats.attendanceRate + '%' : '-' }}
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Available Tests -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 :style="{ margin: 0, fontSize: h2FontSize, fontWeight: 600 }">시험 리스트</h2>
        </div>
      </template>

      <!-- Desktop: Table View -->
      <el-table
        v-if="!isMobile"
        :data="availableTests"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="title" label="시험 제목" min-width="200" />
        <el-table-column label="수업 날짜" width="150">
          <template #default="{ row }">
            {{ row.lessonDate ? new Date(row.lessonDate).toLocaleDateString('ko-KR', {
              year: 'numeric', month: 'long', day: 'numeric'
            }) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="문제 수" width="120">
          <template #default="{ row }">
            {{ row.questionCount || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="className" label="반" width="150" />
        <el-table-column label="상태" width="120">
          <template #default="{ row }">
            <el-tag v-if="getSubmissionForTest(row.id)" type="success">제출 완료</el-tag>
            <el-tag v-else type="warning">미제출</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="점수" width="160">
          <template #default="{ row }">
            <div v-if="getSubmissionForTest(row.id)" style="display: flex; flex-direction: column; gap: 4px">
              <span>{{ getSubmissionForTest(row.id)?.totalScore }}점</span>
              <el-tag
                v-if="getSubmissionForTest(row.id)?.pendingEssayCount > 0"
                type="warning"
                size="small"
                effect="plain"
              >
                ⏳ 서술형 채점 대기 ({{ getSubmissionForTest(row.id)?.pendingEssayCount }}문제)
              </el-tag>
            </div>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!getSubmissionForTest(row.id)"
              type="primary"
              size="small"
              @click="handleTakeTest(row.id)"
            >
              시험 보기
            </el-button>
            <el-button
              v-else
              type="info"
              size="small"
              disabled
            >
              완료
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Mobile: Card View -->
      <div v-else v-loading="loading">
        <el-card
          v-for="test in availableTests"
          :key="test.id"
          shadow="hover"
          :style="{ marginBottom: isMobile ? '10px' : '16px' }"
          :body-style="{ padding: isMobile ? '12px' : '20px' }"
        >
          <div :style="{ display: 'flex', flexDirection: 'column', gap: isMobile ? '8px' : '12px' }">
            <!-- Title and Status -->
            <div :style="{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: isMobile ? '8px' : '12px' }">
              <h3 :style="{ margin: 0, fontSize: h3FontSize, fontWeight: 600, color: '#303133', flex: 1 }">
                {{ test.title }}
              </h3>
              <el-tag v-if="getSubmissionForTest(test.id)" type="success" :size="isMobile ? 'small' : 'default'" :style="{ fontSize: tableFontSize }">
                제출 완료
              </el-tag>
              <el-tag v-else type="warning" :size="isMobile ? 'small' : 'default'" :style="{ fontSize: tableFontSize }">
                미제출
              </el-tag>
            </div>

            <!-- Info Grid -->
            <div :style="{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: isMobile ? '6px' : '8px', fontSize: bodyFontSize, color: '#606266' }">
              <div :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '3px' : '4px' }">
                <el-icon :size="isMobile ? 14 : 16"><Calendar /></el-icon>
                <span>{{ test.lessonDate ? new Date(test.lessonDate).toLocaleDateString('ko-KR', {
                  month: isMobile ? 'numeric' : 'long', day: 'numeric'
                }) : '-' }}</span>
              </div>
              <div :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '3px' : '4px' }">
                <el-icon :size="isMobile ? 14 : 16"><School /></el-icon>
                <span>{{ test.className }}</span>
              </div>
              <div v-if="test.questionCount" :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '3px' : '4px' }">
                <el-icon :size="isMobile ? 14 : 16"><Document /></el-icon>
                <span>{{ test.questionCount }}문제</span>
              </div>
              <div v-if="getSubmissionForTest(test.id)" :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '3px' : '4px' }">
                <el-icon :size="isMobile ? 14 : 16"><Trophy /></el-icon>
                <span style="font-weight: 600; color: #409eff">
                  {{ getSubmissionForTest(test.id)?.totalScore }}점
                </span>
              </div>
              <div v-if="getSubmissionForTest(test.id)?.pendingEssayCount > 0" style="grid-column: span 2">
                <el-tag type="warning" size="small" effect="plain">
                  ⏳ 서술형 채점 대기 ({{ getSubmissionForTest(test.id)?.pendingEssayCount }}문제)
                </el-tag>
              </div>
            </div>

            <!-- Action Button -->
            <el-button
              v-if="!getSubmissionForTest(test.id)"
              type="primary"
              size="small"
              @click="handleTakeTest(test.id)"
              :style="{ width: '100%', marginTop: isMobile ? '0' : '4px' }"
            >
              <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><Edit /></el-icon>
              시험 보기
            </el-button>
            <el-button
              v-else
              type="info"
              size="small"
              disabled
              :style="{ width: '100%', marginTop: isMobile ? '0' : '4px' }"
            >
              <el-icon :style="{ marginRight: isMobile ? '4px' : '8px' }"><Check /></el-icon>
              완료
            </el-button>
          </div>
        </el-card>
      </div>

      <el-empty
        v-if="availableTests.length === 0"
        description="시험이 없습니다"
        style="padding: 60px 0"
      />
    </el-card>

    <!-- Past Tests Dialog -->
    <el-dialog v-model="pastTestsDialogVisible" title="지난 시험 기록" :width="isMobile ? '95%' : '800px'" :fullscreen="isMobile">
      <el-table
        :data="mySubmissions"
        style="width: 100%"
        stripe
        :size="isMobile ? 'small' : 'default'"
        :style="{ fontSize: tableFontSize }"
        :header-cell-style="{ fontSize: tableFontSize, padding: isMobile ? '6px 0' : '12px 0' }"
        :cell-style="{ fontSize: tableFontSize, padding: isMobile ? '6px 8px' : '12px 8px' }"
      >
        <el-table-column label="시험 제목" :min-width="isMobile ? 150 : 200">
          <template #default="{ row }">
            <span :style="{ fontSize: tableFontSize }">{{ row.testTitle || row.test?.title || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="제출 날짜" :width="isMobile ? 100 : 180">
          <template #default="{ row }">
            <span :style="{ fontSize: tableFontSize }">
              {{ row.submittedAt ? new Date(row.submittedAt).toLocaleDateString('ko-KR', {
                year: isMobile ? '2-digit' : 'numeric',
                month: isMobile ? 'numeric' : 'long',
                day: 'numeric'
              }) : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="내 점수" :width="isMobile ? 100 : 150" align="center">
          <template #default="{ row }">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 4px">
              <el-tag
                :type="row.totalScore >= 90 ? 'success' : row.totalScore >= 70 ? 'warning' : 'danger'"
                :size="isMobile ? 'small' : 'default'"
                :style="{ fontSize: tableFontSize }"
              >
                {{ row.totalScore }}점
              </el-tag>
              <el-tag
                v-if="row.pendingEssayCount > 0"
                type="warning"
                size="small"
                effect="plain"
                :style="{ fontSize: tableFontSize }"
              >
                ⏳ 채점 대기 ({{ row.pendingEssayCount }})
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="반 평균" :width="isMobile ? 70 : 100" align="center">
          <template #default="{ row }">
            <span v-if="row.classAverage !== null && row.classAverage !== undefined" :style="{ fontSize: tableFontSize }">
              {{ Math.round(row.classAverage) }}점
            </span>
            <span v-else :style="{ color: '#909399', fontSize: tableFontSize }">-</span>
          </template>
        </el-table-column>
        <el-table-column label="반 등수" :width="isMobile ? 70 : 100" align="center">
          <template #default="{ row }">
            <span v-if="row.rank !== null && row.rank !== undefined" :style="{ fontSize: tableFontSize }">
              {{ row.rank }}등
            </span>
            <span v-else :style="{ color: '#909399', fontSize: tableFontSize }">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="mySubmissions.length === 0"
        description="제출한 시험이 없습니다"
        :image-size="100"
      />

      <template #footer>
        <el-button @click="pastTestsDialogVisible = false">닫기</el-button>
      </template>
    </el-dialog>

    <!-- Attendance Detail Dialog -->
    <el-dialog v-model="attendanceDialogVisible" title="출석 현황" :width="isMobile ? '85%' : '400px'">
      <div v-if="attendanceStats" style="display: flex; flex-direction: column; gap: 16px">
        <div style="text-align: center; margin-bottom: 8px">
          <span :style="{ fontSize: '36px', fontWeight: 700, color: attendanceStats.attendanceRate >= 80 ? '#67C23A' : '#F56C6C' }">
            {{ attendanceStats.attendanceRate }}%
          </span>
          <p style="margin: 4px 0 0; color: #909399; font-size: 13px">전체 {{ attendanceStats.totalLessons }}회 수업</p>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
          <div style="text-align: center; padding: 12px; background: #f0f9eb; border-radius: 8px">
            <div style="font-size: 24px; font-weight: 700; color: #67C23A">{{ attendanceStats.presentCount }}</div>
            <div style="font-size: 13px; color: #67C23A; margin-top: 4px">출석</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #fef0f0; border-radius: 8px">
            <div style="font-size: 24px; font-weight: 700; color: #F56C6C">{{ attendanceStats.absentCount }}</div>
            <div style="font-size: 13px; color: #F56C6C; margin-top: 4px">결석</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #fdf6ec; border-radius: 8px">
            <div style="font-size: 24px; font-weight: 700; color: #E6A23C">{{ attendanceStats.lateCount }}</div>
            <div style="font-size: 13px; color: #E6A23C; margin-top: 4px">지각</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #ecf5ff; border-radius: 8px">
            <div style="font-size: 24px; font-weight: 700; color: #409EFF">{{ attendanceStats.videoCount }}</div>
            <div style="font-size: 13px; color: #409EFF; margin-top: 4px">인강</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="attendanceDialogVisible = false">닫기</el-button>
      </template>
    </el-dialog>
  </div>
</template>
