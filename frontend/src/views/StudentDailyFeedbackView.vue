<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dailyFeedbackAPI, aiFeedbackAPI, type DailyFeedback, type EssayDetail, authAPI, lessonAPI, type Lesson, studentAPI } from '../api/client'
import { useRouter, useRoute } from 'vue-router'
import { useBreakpoint } from '@/composables/useBreakpoint'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const feedback = ref<DailyFeedback | null>(null)
const currentUser = ref<any>(null)
const lessons = ref<Lesson[]>([])
const selectedLessonId = ref<number | null>(null)
const selectedLesson = ref<Lesson | null>(null)
const studentId = ref<number | null>(null)
const studentName = ref<string>('')
const isTeacherView = computed(() => route.params.id !== undefined)

// 피드백 편집 상태
const isEditingFeedback = ref(false)
const editedFeedback = ref('')
const editedAuthorName = ref('')

// AI 피드백 생성 상태
const isGeneratingAiFeedback = ref(false)
const usedAiFeedback = ref(false)

const generateAiFeedback = async () => {
  if (!feedback.value?.todayTest || !studentId.value || !selectedLessonId.value) return
  isGeneratingAiFeedback.value = true
  try {
    const response = await aiFeedbackAPI.generate({
      studentId: studentId.value,
      lessonId: selectedLessonId.value,
      teacherId: currentUser.value.userId
    })
    editedFeedback.value = response.generatedFeedback
    usedAiFeedback.value = true
  } catch (error) {
    ElMessage.error('AI 피드백 생성에 실패했습니다')
  } finally {
    isGeneratingAiFeedback.value = false
  }
}

const { isMobile } = useBreakpoint()

const containerPadding = computed(() => isMobile.value ? '12px' : '24px')
const h1FontSize = computed(() => isMobile.value ? '16px' : isTeacherView.value ? '28px' : '32px')
const h3FontSize = computed(() => isMobile.value ? '12px' : '16px')
const sectionTitleFontSize = computed(() => isMobile.value ? '14px' : '20px')
const labelFontSize = computed(() => isMobile.value ? '12px' : '16px')
const textFontSize = computed(() => isMobile.value ? '11px' : '15px')
const smallTextFontSize = computed(() => isMobile.value ? '10px' : '13px')
const tableFontSize = computed(() => isMobile.value ? '10px' : '14px')

const fetchLessons = async () => {
  try {
    const userRes = await authAPI.getCurrentUser()
    currentUser.value = userRes.data

    if (!currentUser.value.userId) {
      router.push('/login')
      return
    }

    // 선생님이 학생 피드백을 보는 경우와 학생이 본인 피드백을 보는 경우 구분
    if (isTeacherView.value) {
      studentId.value = Number(route.params.id)
    } else {
      studentId.value = currentUser.value.userId
    }

    // 학생 정보에서 classId 가져오기
    const studentRes = await studentAPI.getStudent(studentId.value)
    studentName.value = studentRes.data.name
    const classId = studentRes.data.classId

    if (classId) {
      const lessonsRes = await lessonAPI.getLessonsByClass(classId)
      lessons.value = lessonsRes.data

      // 오늘 날짜와 가장 가까운 레슨 자동 선택
      const now = new Date()
      const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
      const todayLesson = lessons.value.find(l => l.lessonDate === today)

      if (todayLesson?.id) {
        selectedLessonId.value = todayLesson.id
      } else if (lessons.value.length > 0 && lessons.value[0].id) {
        // 오늘 레슨이 없으면 가장 최근 레슨 선택
        selectedLessonId.value = lessons.value[0].id
      }

      // 선택된 레슨의 피드백 가져오기
      if (selectedLessonId.value) {
        await fetchFeedback()
      }
    }
  } catch (error) {
    ElMessage.error('수업 목록을 불러오는데 실패했습니다')
  }
}

const fetchFeedback = async () => {
  if (!selectedLessonId.value || !studentId.value) return

  loading.value = true
  try {
    // Fetch lesson details
    const lessonRes = await lessonAPI.getLesson(selectedLessonId.value)
    selectedLesson.value = lessonRes.data

    // Fetch feedback
    const feedbackRes = await dailyFeedbackAPI.getDailyFeedback(
      studentId.value,
      selectedLessonId.value
    )
    feedback.value = feedbackRes.data
  } catch (error: any) {
    if (error.response?.status === 404) {
      ElMessage.warning('해당 수업의 피드백이 없습니다')
      feedback.value = null
    } else {
      ElMessage.error('피드백을 불러오는데 실패했습니다')
    }
  } finally {
    loading.value = false
  }
}

const onLessonChange = () => {
  fetchFeedback()
}

const todayHomeworkStatus = computed(() => {
  if (!feedback.value?.todayHomework) return null
  const hw = feedback.value.todayHomework
  if (hw.completion === null || hw.completion === undefined) {
    return { status: 'not-started', text: '미완료', color: '#909399' }
  } else if (hw.completion >= 80) {
    return { status: 'excellent', text: '완료', color: '#67c23a' }
  } else if (hw.completion >= 50) {
    return { status: 'good', text: '진행중', color: '#e6a23c' }
  } else {
    return { status: 'needs-work', text: '미흡', color: '#f56c6c' }
  }
})


const incorrectQuestionsWithRate = computed(() => {
  if (!feedback.value?.todayTest) return []

  const incorrectSet = new Set(feedback.value.todayTest.incorrectQuestions)
  const accuracyMap = new Map(
    feedback.value.todayTest.questionAccuracyRates.map(q => [q.questionNumber, q.correctRate])
  )

  return feedback.value.todayTest.incorrectQuestions
    .map(qNum => ({
      questionNumber: qNum,
      academyCorrectRate: accuracyMap.get(qNum) || 0,
      difficulty: getDifficulty(accuracyMap.get(qNum) || 0)
    }))
    .sort((a, b) => a.questionNumber - b.questionNumber)
})

const getDifficulty = (correctRate: number) => {
  if (correctRate >= 80) return { level: 'easy', text: '쉬움', color: '#67c23a' }
  if (correctRate >= 60) return { level: 'medium', text: '보통', color: '#409eff' }
  if (correctRate >= 40) return { level: 'hard', text: '어려움', color: '#e6a23c' }
  return { level: 'very-hard', text: '매우 어려움', color: '#f56c6c' }
}

const startEditingFeedback = async () => {
  editedFeedback.value = feedback.value?.instructorFeedback || ''
  editedAuthorName.value = feedback.value?.feedbackAuthor || ''

  // 작성자 이름이 없으면 현재 사용자 이름 가져오기
  if (!editedAuthorName.value) {
    try {
      const userRes = await authAPI.getCurrentUser()
      editedAuthorName.value = userRes.data.name || ''
    } catch (error) {
      console.error('Failed to get current user')
    }
  }

  isEditingFeedback.value = true
}

const cancelEditingFeedback = () => {
  isEditingFeedback.value = false
  editedFeedback.value = ''
  editedAuthorName.value = ''
  usedAiFeedback.value = false
}

const saveFeedback = async () => {
  if (!editedFeedback.value.trim()) {
    ElMessage.warning('피드백 내용을 입력해주세요')
    return
  }

  if (!selectedLessonId.value || !studentId.value) {
    ElMessage.error('수업 또는 학생 정보를 찾을 수 없습니다')
    return
  }

  try {
    await dailyFeedbackAPI.updateInstructorFeedback(
      studentId.value,
      selectedLessonId.value,
      editedFeedback.value,
      editedAuthorName.value,
      usedAiFeedback.value
    )

    ElMessage.success('피드백이 저장되었습니다')
    isEditingFeedback.value = false
    usedAiFeedback.value = false

    // 피드백 다시 불러오기
    await fetchFeedback()
  } catch (error) {
    ElMessage.error('피드백 저장에 실패했습니다')
  }
}

onMounted(() => {
  fetchLessons()
})
</script>

<template>
  <div :class="{ 'student-view': !isTeacherView, 'teacher-view': isTeacherView }" v-loading="loading" :style="{ padding: containerPadding, maxWidth: '1200px', margin: '0 auto' }">
    <!-- 선생님용 헤더 (뒤로가기 버튼 포함) -->
    <el-card v-if="isTeacherView" shadow="never" style="margin-bottom: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <h1 :style="{ margin: '0 0 8px', fontSize: h1FontSize, fontWeight: 600, color: '#303133' }">
            {{ studentName }}님의 수업 피드백
          </h1>
        </div>
        <el-button @click="router.push(`/students/${studentId}`)" :icon="ArrowLeft" size="large">
          학생 상세로
        </el-button>
      </div>
    </el-card>

    <!-- 공통 헤더 -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div style="text-align: center">
        <h1 v-if="!isTeacherView" :style="{ margin: '0 0 8px', fontSize: h1FontSize, fontWeight: 600, color: '#303133' }">
          수업 피드백
        </h1>
        <p v-if="feedback" :style="{ margin: '0 0 16px', color: '#909399', fontSize: labelFontSize }">
          {{ new Date(feedback.lessonDate).toLocaleDateString('ko-KR', {
            year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
          }) }}
        </p>

        <!-- 수업 선택 -->
        <div style="max-width: 400px; margin: 0 auto">
          <el-select
            v-model="selectedLessonId"
            placeholder="수업을 선택하세요"
            style="width: 100%"
            @change="onLessonChange"
            size="large"
          >
            <el-option
              v-for="lesson in lessons"
              :key="lesson.id"
              :label="new Date(lesson.lessonDate).toLocaleDateString('ko-KR', {
                year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
              })"
              :value="lesson.id"
            />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- Announcement (공지사항 - 맨 위) -->
    <el-card v-if="selectedLesson?.announcement" shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon size="20" color="#e6a23c"><BellFilled /></el-icon>
          <span :style="{ fontWeight: 600, fontSize: labelFontSize }">공지사항</span>
        </div>
      </template>
      <div :style="{
        background: '#fef0f0',
        padding: '20px',
        borderRadius: '8px',
        lineHeight: '1.8',
        fontSize: textFontSize,
        color: '#303133',
        whiteSpace: 'pre-wrap'
      }">
        {{ selectedLesson.announcement }}
      </div>
    </el-card>

    <div v-if="feedback">
      <el-row :gutter="24" style="margin-bottom: 24px">
        <el-col :xs="24" :sm="24" :md="12">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon size="20" color="#409eff"><Document /></el-icon>
                <span :style="{ fontWeight: 600, fontSize: labelFontSize }">오늘의 숙제</span>
              </div>
            </template>

            <div v-if="feedback.todayHomework">
              <div :style="{ fontSize: sectionTitleFontSize, fontWeight: 600, marginBottom: isMobile ? '12px' : '16px' }">
                {{ feedback.todayHomework.homeworkTitle }}
              </div>

              <el-descriptions :column="1" border :size="isMobile ? 'small' : 'default'" :style="{ fontSize: tableFontSize }">
                <el-descriptions-item label="문제 수" :label-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }" :content-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }">
                  {{ feedback.todayHomework.questionCount }}문제
                </el-descriptions-item>
                <el-descriptions-item label="완성도" :label-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }" :content-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }">
                  <div :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '8px' : '12px' }">
                    <el-progress
                      :percentage="feedback.todayHomework.completion || 0"
                      :color="todayHomeworkStatus?.color"
                      style="flex: 1"
                      :stroke-width="isMobile ? 4 : 6"
                    />
                  </div>
                  <div v-if="feedback.todayHomework.incorrectCount !== null && feedback.todayHomework.incorrectCount !== undefined" :style="{ marginTop: isMobile ? '6px' : '8px', fontSize: smallTextFontSize }">
                    <div style="color: #f56c6c; font-weight: 500">
                      오답 개수: {{ feedback.todayHomework.incorrectCount }}개
                    </div>
                    <div style="color: #e6a23c; font-weight: 500; margin-top: 4px">
                      안 푼 문제: {{ feedback.todayHomework.unsolvedCount || 0 }}개
                    </div>
                    <div style="color: #909399; margin-top: 4px">
                      전체: {{ feedback.todayHomework.questionCount }}문제
                    </div>
                  </div>
                </el-descriptions-item>
                <el-descriptions-item label="마감일" v-if="feedback.todayHomework.dueDate" :label-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }" :content-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }">
                  {{ new Date(feedback.todayHomework.dueDate).toLocaleDateString('ko-KR') }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <el-empty v-else description="오늘 숙제가 없습니다" :image-size="80" />
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon size="20" color="#67c23a"><Calendar /></el-icon>
                <span :style="{ fontWeight: 600, fontSize: labelFontSize }">다음 수업 숙제</span>
              </div>
            </template>

            <div v-if="feedback.nextHomework">
              <div :style="{ fontSize: sectionTitleFontSize, fontWeight: 600, marginBottom: isMobile ? '12px' : '16px' }">
                {{ feedback.nextHomework.homeworkTitle }}
              </div>

              <el-descriptions :column="1" border :size="isMobile ? 'small' : 'default'" :style="{ fontSize: tableFontSize }">
                <el-descriptions-item label="문제 수" :label-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }" :content-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }">
                  {{ feedback.nextHomework.questionCount }}문제
                </el-descriptions-item>
                <el-descriptions-item label="마감일" v-if="feedback.nextHomework.dueDate" :label-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }" :content-style="{ fontSize: tableFontSize, padding: isMobile ? '8px' : '12px' }">
                  {{ new Date(feedback.nextHomework.dueDate).toLocaleDateString('ko-KR') }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <el-empty v-else description="다음 숙제가 없습니다" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="feedback.todayTest" shadow="never" style="margin-bottom: 24px">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon size="20" color="#e6a23c"><EditPen /></el-icon>
              <span :style="{ fontWeight: 600, fontSize: labelFontSize }">오늘의 시험 결과</span>
            </div>
            <el-tag :type="'success'" size="large">
              {{ feedback.todayTest.studentScore }}점
            </el-tag>
          </div>
        </template>

        <div style="margin-bottom: 20px">
          <h3 :style="{ margin: '0 0 12px', fontSize: h3FontSize, color: '#303133' }">
            {{ feedback.todayTest.testTitle }}
          </h3>
          <div style="display: flex; gap: 16px; align-items: center">
            <div style="padding: 8px 16px; background: #f0f9ff; border-radius: 6px; border-left: 3px solid #409eff">
              <span :style="{ color: '#909399', fontSize: smallTextFontSize }">반 평균</span>
              <span :style="{ marginLeft: '8px', fontWeight: 600, color: '#303133', fontSize: textFontSize }">
                {{ Math.round(feedback.todayTest.classAverage) }}점
              </span>
            </div>
            <div style="padding: 8px 16px; background: #fef0f0; border-radius: 6px; border-left: 3px solid #f56c6c">
              <span :style="{ color: '#909399', fontSize: smallTextFontSize }">반 등수</span>
              <span :style="{ marginLeft: '8px', fontWeight: 600, color: '#303133', fontSize: textFontSize }">
                {{ feedback.todayTest.rank }}등
              </span>
            </div>
          </div>
        </div>

        <div v-if="incorrectQuestionsWithRate.length > 0">
          <div :style="{ fontWeight: 600, marginBottom: isMobile ? '8px' : '12px', color: '#f56c6c', fontSize: labelFontSize }">
            틀린 문제 분석 ({{ incorrectQuestionsWithRate.length }}개)
          </div>
          <el-table
            :data="incorrectQuestionsWithRate"
            stripe
            :style="{ fontSize: tableFontSize }"
            :size="isMobile ? 'small' : 'default'"
            :header-cell-style="{ fontSize: tableFontSize, padding: isMobile ? '6px 0' : '12px 0' }"
            :cell-style="{ fontSize: tableFontSize, padding: isMobile ? '6px 2px' : '12px 8px' }"
          >
            <el-table-column prop="questionNumber" label="문제 번호" :width="isMobile ? 70 : 120" align="center">
              <template #default="{ row }">
                <el-tag type="danger" :size="isMobile ? 'small' : 'default'" :style="{ fontSize: tableFontSize }">{{ row.questionNumber }}번</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="학원 평균 정답률" align="center">
              <template #default="{ row }">
                <div :style="{ display: 'flex', alignItems: 'center', gap: isMobile ? '4px' : '8px' }">
                  <el-progress
                    :percentage="Math.round(row.academyCorrectRate)"
                    :color="row.difficulty.color"
                    :show-text="false"
                    :style="{flex: 1, fontSize: tableFontSize}"
                    :stroke-width="isMobile ? 4 : 6"
                  />
                  <span :style="{ fontWeight: 300, color: '#606266', fontSize: tableFontSize, whiteSpace: 'nowrap' }">
                    {{ Math.round(row.academyCorrectRate) }}%
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="난이도" :width="isMobile ? 90 : 130" align="center">
              <template #default="{ row }">
                <el-tag :color="row.difficulty.color" :size="isMobile ? 'small' : 'default'" :style="{ color: 'white', border: 'none', fontSize: tableFontSize }">
                  {{ row.difficulty.text }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-empty v-else description="모든 문제를 맞췄습니다! 훌륭해요!"
                  :image-size="80">
          <template #image>
            <el-icon size="80" color="#67c23a"><CircleCheck /></el-icon>
          </template>
        </el-empty>

        <!-- 서술형 답안 섹션 -->
        <div v-if="feedback.todayTest.essayDetails && feedback.todayTest.essayDetails.length > 0"
             :style="{ marginTop: isMobile ? '16px' : '24px' }">
          <div :style="{ fontWeight: 600, marginBottom: isMobile ? '8px' : '12px', color: '#e6a23c', fontSize: labelFontSize }">
            서술형 답안 ({{ feedback.todayTest.essayDetails.length }}문제)
          </div>
          <div
            v-for="essay in feedback.todayTest.essayDetails"
            :key="essay.questionNumber"
            :style="{
              border: '1px solid #ebeef5',
              borderRadius: '8px',
              padding: isMobile ? '12px' : '16px',
              marginBottom: isMobile ? '10px' : '12px',
              borderLeft: essay.earnedPoints != null ? '4px solid #67c23a' : '4px solid #e6a23c'
            }"
          >
            <!-- 문제 번호 + 채점 상태 -->
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-tag type="warning" :size="isMobile ? 'small' : 'default'">{{ essay.questionNumber }}번 (서술형)</el-tag>
                <el-tag type="info" effect="plain" :size="isMobile ? 'small' : 'default'" :style="{ fontSize: smallTextFontSize }">
                  만점 {{ essay.maxPoints }}점
                </el-tag>
              </div>
              <!-- 채점 완료 -->
              <div v-if="essay.earnedPoints != null" style="display: flex; align-items: center; gap: 6px">
                <el-tag type="success" :size="isMobile ? 'small' : 'default'">
                  {{ essay.earnedPoints }} / {{ essay.maxPoints }}점
                </el-tag>
              </div>
              <!-- 채점 대기 -->
              <el-tag v-else type="warning" effect="plain" :size="isMobile ? 'small' : 'default'">
                ⏳ 채점 대기 중
              </el-tag>
            </div>

            <!-- 내 답안 -->
            <div :style="{
              background: '#f8f9fa',
              borderRadius: '6px',
              padding: isMobile ? '8px 10px' : '10px 14px',
              fontSize: smallTextFontSize,
              color: '#606266',
              whiteSpace: 'pre-wrap',
              lineHeight: '1.7',
              marginBottom: essay.teacherComment ? '10px' : '0'
            }">
              <span :style="{ color: '#909399', fontSize: smallTextFontSize }">내 답안 </span>
              {{ essay.studentAnswer || '(미작성)' }}
            </div>

            <!-- 선생님 코멘트 -->
            <div v-if="essay.teacherComment" :style="{
              background: '#f0f9ff',
              borderRadius: '6px',
              padding: isMobile ? '8px 10px' : '10px 14px',
              fontSize: smallTextFontSize,
              color: '#303133',
              marginTop: '8px',
              borderLeft: '3px solid #409eff'
            }">
              <span :style="{ color: '#409eff', fontWeight: 600, marginRight: '6px', fontSize: smallTextFontSize }">선생님 코멘트</span>
              {{ essay.teacherComment }}
            </div>
          </div>
        </div>
      </el-card>

      <!-- 시험 없는 경우 표시 -->
      <el-card v-else shadow="never" style="margin-bottom: 24px">
        <template #header>
          <div style="display: flex; align-items: center; gap: 8px">
            <el-icon size="20" color="#909399"><EditPen /></el-icon>
            <span :style="{ fontWeight: 600, fontSize: labelFontSize }">시험 결과</span>
          </div>
        </template>
        <el-empty description="응시한 시험이 없습니다" :image-size="80">
          <template #image>
            <el-icon size="80" color="#c0c4cc"><Document /></el-icon>
          </template>
        </el-empty>
      </el-card>

      <!-- Common Feedback (수업 공통 피드백 - 개별 피드백 위) -->
      <el-card v-if="selectedLesson?.commonFeedback" shadow="never" style="margin-bottom: 24px">
        <template #header>
          <div style="display: flex; align-items: center; gap: 8px">
            <el-icon size="20" color="#409eff"><ChatLineSquare /></el-icon>
            <span :style="{ fontWeight: 600, fontSize: labelFontSize }">수업 공통 피드백</span>
          </div>
        </template>
        <div :style="{
          background: '#f5f7fa',
          padding: '20px',
          borderRadius: '8px',
          lineHeight: '1.8',
          fontSize: textFontSize,
          color: '#303133',
          whiteSpace: 'pre-wrap'
        }">
          {{ selectedLesson.commonFeedback }}
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon size="20" color="#f56c6c"><ChatDotRound /></el-icon>
              <span :style="{ fontWeight: 600, fontSize: labelFontSize }">개별 피드백</span>
            </div>
            <el-button
              v-if="isTeacherView && !isEditingFeedback"
              type="primary"
              size="small"
              @click="startEditingFeedback"
            >
              <el-icon style="margin-right: 4px"><Edit /></el-icon>
              {{ feedback.instructorFeedback ? '편집' : '작성' }}
            </el-button>
          </div>
        </template>

        <!-- 편집 모드 -->
        <div v-if="isEditingFeedback">
          <el-form label-width="80px">
            <el-form-item label="작성자">
              <el-input v-model="editedAuthorName" placeholder="선생님 이름" />
            </el-form-item>
            <el-form-item label="피드백">
              <div v-if="feedback?.todayTest" style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
                <el-button
                  type="primary"
                  plain
                  :loading="isGeneratingAiFeedback"
                  @click="generateAiFeedback"
                >
                  AI 피드백 생성
                </el-button>
                <el-button
                  text
                  size="small"
                  @click="router.push('/settings/feedback-prompt')"
                  style="color: #909399"
                >
                  <el-icon style="margin-right: 4px"><Setting /></el-icon>
                  프롬프트 설정
                </el-button>
              </div>
              <el-input
                v-model="editedFeedback"
                type="textarea"
                :rows="10"
                placeholder="학생에게 전달할 개별 피드백을 작성하세요..."
              />
            </el-form-item>
          </el-form>
          <div style="display: flex; gap: 12px; justify-content: flex-end">
            <el-button @click="cancelEditingFeedback">취소</el-button>
            <el-button type="primary" @click="saveFeedback">저장</el-button>
          </div>
        </div>

        <!-- 읽기 모드 -->
        <div v-else>
          <div v-if="feedback.instructorFeedback">
            <div style="margin-bottom: 12px">
              <el-tag type="success">{{ feedback.feedbackAuthor || '선생님' }}</el-tag>
            </div>
            <div :style="{
              background: '#f5f7fa',
              padding: '20px',
              borderRadius: '8px',
              lineHeight: '1.8',
              fontSize: textFontSize,
              color: '#303133',
              whiteSpace: 'pre-wrap'
            }">
              {{ feedback.instructorFeedback }}
            </div>
          </div>
          <el-empty v-else description="아직 개별 피드백이 없습니다" :image-size="80">
            <template #image>
              <el-icon size="80" color="#c0c4cc"><ChatDotRound /></el-icon>
            </template>
          </el-empty>
        </div>
      </el-card>
    </div>

    <el-empty v-else-if="!loading" description="오늘 수업이 없습니다" :image-size="120" />
  </div>
</template>

<style scoped>
.el-card {
  border-radius: 8px;
}

.el-descriptions :deep(.el-descriptions__label) {
  font-weight: 600;
}
</style>
