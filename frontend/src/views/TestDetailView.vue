<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { testAPI, submissionAPI, type Test, type Submission, type SubmissionDetail } from '../api/client'

const route = useRoute()
const router = useRouter()
const testId = route.params.id as string

const loading = ref(false)
const recalculating = ref(false)
const test = ref<Test | null>(null)
const submissions = ref<Submission[]>([])
const stats = ref<any>(null)

const fetchTestDetail = async () => {
  loading.value = true
  try {
    const [testResponse, submissionsResponse, statsResponse] = await Promise.all([
      testAPI.getTest(Number(testId)),
      submissionAPI.getByTestId(Number(testId)),
      testAPI.getTestStats(Number(testId))
    ])

    test.value = testResponse.data
    submissions.value = submissionsResponse.data.content || submissionsResponse.data
    stats.value = statsResponse.data
  } catch (error) {
    ElMessage.error('시험 정보를 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const recalculateScores = async () => {
  try {
    await ElMessageBox.confirm(
      '모든 학생의 점수를 재계산합니다. 계속하시겠습니까?',
      '재채점 확인',
      {
        confirmButtonText: '재채점',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    recalculating.value = true
    await testAPI.recalculateScores(Number(testId))
    ElMessage.success('재채점이 완료되었습니다.')

    // 데이터 새로고침
    await fetchTestDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('재채점에 실패했습니다.')
    }
  } finally {
    recalculating.value = false
  }
}

const essayGradeVisible = ref(false)
const gradingSubmission = ref<any>(null)
const gradingDetails = ref<any[]>([])
const grading = ref(false)

const openEssayGrade = async (submission: any) => {
  const res = await submissionAPI.getSubmissionWithDetails(submission.id)
  const details: SubmissionDetail[] = (res.data as any).details || []
  gradingDetails.value = details
    .filter((d: SubmissionDetail) => d.questionType === 'ESSAY')
    .map((d: SubmissionDetail) => ({
      ...d,
      inputScore: d.earnedPoints ?? null,
      inputComment: d.teacherComment ?? ''
    }))
  gradingSubmission.value = submission
  essayGradeVisible.value = true
}

const handleGradeEssay = async () => {
  grading.value = true
  try {
    for (const detail of gradingDetails.value) {
      if (detail.inputScore !== null && detail.inputScore !== undefined) {
        await submissionAPI.gradeEssay(detail.id, detail.inputScore, detail.inputComment)
      }
    }
    ElMessage.success('서술형 채점이 완료되었습니다')
    essayGradeVisible.value = false
    await fetchTestDetail()
  } catch (error) {
    ElMessage.error('채점에 실패했습니다')
  } finally {
    grading.value = false
  }
}

const navigateToAnswers = () => {
  router.push(`/tests/${testId}/answers`)
}

const goBack = () => {
  router.push('/tests')
}

onMounted(() => {
  fetchTestDetail()
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
              <Document />
            </el-icon>
            {{ test?.title || '시험 상세보기' }}
          </h1>
        </div>
        <div style="display: flex; gap: 12px">
          <el-button
            type="primary"
            @click="recalculateScores"
            :loading="recalculating"
            :icon="Refresh"
          >
            재채점
          </el-button>
          <el-button type="warning" @click="navigateToAnswers" :icon="EditPen">
            정답 관리
          </el-button>
          <el-button @click="goBack" :icon="ArrowLeft">
            목록으로
          </el-button>
        </div>
      </div>
    </el-card>

    <div v-loading="loading">
      <!-- Test Info -->
      <el-row :gutter="24" style="margin-bottom: 24px">
        <el-col :span="16">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#67c23a">
                  <InfoFilled />
                </el-icon>
                <span style="font-weight: 600">시험 정보</span>
              </div>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="시험명" label-align="right">
                <span style="font-weight: 500">{{ test?.title }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="문제 수" label-align="right">
                <el-tag type="info">{{ test?.questionCount || 0 }}문제</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="등록일" label-align="right">
                {{ test?.createdAt ? new Date(test.createdAt).toLocaleString('ko-KR') : '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#e6a23c">
                  <TrendCharts />
                </el-icon>
                <span style="font-weight: 600">통계</span>
              </div>
            </template>

            <div style="text-align: center">
              <div style="margin-bottom: 20px">
                <div style="font-size: 32px; font-weight: 600; color: #409eff; margin-bottom: 4px">
                  {{ submissions.length }}
                </div>
                <div style="color: #909399; font-size: 14px">총 응시자 수</div>
              </div>

              <div style="display: flex; justify-content: space-around">
                <div>
                  <div style="font-size: 20px; font-weight: 500; color: #67c23a; margin-bottom: 4px">
                    {{ stats?.averageScore || 0 }}점
                  </div>
                  <div style="color: #909399; font-size: 12px">평균 점수</div>
                </div>
                <div>
                  <div style="font-size: 20px; font-weight: 500; color: #e6a23c; margin-bottom: 4px">
                    {{ stats?.maxScore || 0 }}점
                  </div>
                  <div style="color: #909399; font-size: 12px">최고 점수</div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Question Stats and Student Rankings -->
      <el-row :gutter="24" style="margin-bottom: 24px">
        <!-- Question Stats -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#f56c6c">
                  <DataAnalysis />
                </el-icon>
                <span style="font-weight: 600">문항별 정답률</span>
              </div>
            </template>

            <el-table
              :data="stats?.questionStats || []"
              style="width: 100%"
              stripe
              :max-height="400"
            >
              <el-table-column prop="questionNumber" label="문제 번호" width="120" align="center">
                <template #default="{ row }">
                  <div style="display: flex; flex-direction: column; align-items: center; gap: 4px">
                    <el-tag type="info" size="large">{{ row.questionNumber }}번</el-tag>
                    <el-tag v-if="row.questionType === 'ESSAY'" type="warning" size="small" effect="plain">서술형</el-tag>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="유형 / 해설" width="180">
                <template #default="{ row }">
                  <div style="display: flex; flex-direction: column; gap: 4px">
                    <span v-if="row.topic" style="color: #409eff; font-size: 13px; font-weight: 500">
                      {{ row.topic }}
                    </span>
                    <span v-else style="color: #c0c4cc; font-size: 12px">유형 미지정</span>
                    <a
                      v-if="row.videoLink"
                      :href="row.videoLink"
                      target="_blank"
                      rel="noopener"
                      style="color: #409eff; font-size: 12px; text-decoration: none"
                    >▶ 해설 영상</a>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="정답률 / 획득률" width="240" align="center">
                <template #default="{ row }">
                  <!-- 서술형: 평균 획득률 -->
                  <div v-if="row.questionType === 'ESSAY'">
                    <div v-if="row.avgEarnedRate != null" style="display: flex; align-items: center; gap: 8px">
                      <el-progress
                        :percentage="Math.round(row.avgEarnedRate)"
                        :color="row.avgEarnedRate >= 70 ? '#67c23a' : row.avgEarnedRate >= 50 ? '#e6a23c' : '#f56c6c'"
                        :stroke-width="10"
                        style="flex: 1"
                      />
                    </div>
                    <el-tag v-else type="info" effect="plain" size="small">채점 대기 중</el-tag>
                  </div>
                  <!-- 객관식/주관식: 정답률 -->
                  <div v-else style="display: flex; align-items: center; gap: 8px">
                    <el-progress
                      :percentage="Math.round(row.correctRate)"
                      :color="row.correctRate >= 70 ? '#67c23a' : row.correctRate >= 50 ? '#e6a23c' : '#f56c6c'"
                      :stroke-width="10"
                      style="flex: 1"
                    />
                  </div>
                </template>
              </el-table-column>

              <el-table-column min-width="200">
                <template #header>
                  <span>틀린 학생</span>
                  <span style="color: #e6a23c; font-size: 11px; margin-left: 4px">(서술형: 미채점)</span>
                </template>
                <template #default="{ row }">
                  <!-- 서술형: 미채점 학생 -->
                  <div v-if="row.questionType === 'ESSAY'">
                    <div v-if="row.incorrectStudents && row.incorrectStudents.length > 0" style="display: flex; flex-wrap: wrap; gap: 4px">
                      <el-tag
                        v-for="(student, index) in row.incorrectStudents"
                        :key="index"
                        type="warning"
                        size="small"
                        effect="plain"
                      >
                        {{ student }}
                      </el-tag>
                    </div>
                    <span v-else style="color: #67c23a; font-weight: 500">전원 채점 완료 ✓</span>
                  </div>
                  <!-- 객관식/주관식: 틀린 학생 -->
                  <div v-else>
                    <div v-if="row.incorrectStudents && row.incorrectStudents.length > 0" style="display: flex; flex-wrap: wrap; gap: 4px">
                      <el-tag
                        v-for="(student, index) in row.incorrectStudents"
                        :key="index"
                        type="danger"
                        size="small"
                        effect="plain"
                      >
                        {{ student }}
                      </el-tag>
                    </div>
                    <span v-else style="color: #67c23a; font-weight: 500">전원 정답 ✓</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-if="!stats?.questionStats || stats.questionStats.length === 0"
              description="문항 통계가 없습니다" :image-size="80" />
          </el-card>
        </el-col>

        <!-- Student Rankings -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#e6a23c">
                  <TrophyBase />
                </el-icon>
                <span style="font-weight: 600">학생 순위</span>
              </div>
            </template>

            <el-table
              :data="stats?.studentScores || []"
              style="width: 100%"
              stripe
              :max-height="400"
              :row-style="{ cursor: 'pointer' }"
              @row-click="(row: any) => row.studentId && router.push(`/tests/${testId}/students/${row.studentId}/result`)"
            >
              <el-table-column label="순위" width="80" align="center">
                <template #default="{ $index }">
                  <div style="font-weight: 600; font-size: 16px">
                    <span v-if="$index === 0" style="color: #ffd700">🥇</span>
                    <span v-else-if="$index === 1" style="color: #c0c0c0">🥈</span>
                    <span v-else-if="$index === 2" style="color: #cd7f32">🥉</span>
                    <span v-else style="color: #909399">{{ $index + 1 }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="studentName" label="학생명" min-width="120">
                <template #default="{ row }">
                  <div style="display: flex; align-items: center; gap: 8px">
                    <el-avatar size="small" :icon="UserFilled" />
                    <span style="font-weight: 500">{{ row.studentName }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="totalScore" label="점수" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="row.totalScore >= 80 ? 'success' : row.totalScore >= 60 ? 'warning' : 'danger'"
                    size="large"
                  >
                    {{ row.totalScore }}점
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="결과" width="110" align="center">
                <template #default="{ row }">
                  <el-button
                    v-if="row.studentId"
                    size="small"
                    type="primary"
                    plain
                    @click.stop="router.push(`/tests/${testId}/students/${row.studentId}/result`)"
                  >
                    결과 보기
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-if="!stats?.studentScores || stats.studentScores.length === 0"
              description="응시한 학생이 없습니다" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>

      <!-- Submissions -->
      <el-card shadow="never">
        <template #header>
          <div style="display: flex; align-items: center; gap: 8px">
            <el-icon color="#409eff">
              <User />
            </el-icon>
            <span style="font-weight: 600">응시 현황</span>
          </div>
        </template>

        <el-table
          :data="submissions"
          style="width: 100%"
          stripe
        >
          <el-table-column prop="student.name" label="학생명" min-width="120">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-avatar size="small" :icon="UserFilled" />
                <span style="font-weight: 500">{{ row.student?.name || '알 수 없음' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="totalScore" label="점수" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.totalScore >= 80 ? 'success' : row.totalScore >= 60 ? 'warning' : 'danger'"
                size="large"
              >
                {{ row.totalScore }}점
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="응시일" width="250">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon color="#909399">
                  <Calendar />
                </el-icon>
                {{ row.submittedAt ? new Date(row.submittedAt).toLocaleString('ko-KR') : '-' }}
              </div>
            </template>
          </el-table-column>

          <el-table-column label="학생 정보" min-width="200">
            <template #default="{ row }">
              <div style="font-size: 13px; color: #606266">
                <div>🏫 {{ row.student?.school || '-' }}</div>
                <div style="margin-top: 4px">📚 {{ row.student?.grade || '-' }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="서술형 채점" width="140" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.pendingEssayCount > 0"
                size="small"
                type="warning"
                @click="openEssayGrade(row)"
              >
                채점 ({{ row.pendingEssayCount }}문제)
              </el-button>
              <el-button
                v-else-if="row.pendingEssayCount === 0"
                size="small"
                type="success"
                plain
                @click="openEssayGrade(row)"
              >
                채점 완료 (수정)
              </el-button>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="submissions.length === 0" description="아직 응시한 학생이 없습니다" />
      </el-card>
    </div>

    <!-- 서술형 채점 다이얼로그 -->
    <el-dialog
      v-model="essayGradeVisible"
      :title="`서술형 채점${gradingSubmission?.pendingEssayCount === 0 ? ' 수정' : ''} - ${gradingSubmission?.student?.name}`"
      width="600px"
    >
      <div v-for="detail in gradingDetails" :key="detail.id" style="margin-bottom: 24px; padding-bottom: 24px; border-bottom: 1px solid #eee">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
          <strong>{{ detail.questionNumber }}번 문제</strong>
          <el-tag type="info">최대 {{ detail.maxPoints }}점</el-tag>
        </div>

        <el-card shadow="never" style="margin-bottom: 12px; background: #f8f9fa">
          <div style="font-size: 13px; color: #606266; white-space: pre-wrap">
            {{ detail.studentAnswer || '(미작성)' }}
          </div>
        </el-card>

        <el-row :gutter="12">
          <el-col :span="10">
            <div style="font-size: 12px; color: #909399; margin-bottom: 4px">점수 (0 ~ {{ detail.maxPoints }})</div>
            <el-input-number
              v-model="detail.inputScore"
              :min="0"
              :max="detail.maxPoints"
              :precision="1"
              :step="0.5"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="14">
            <div style="font-size: 12px; color: #909399; margin-bottom: 4px">코멘트 (선택)</div>
            <el-input v-model="detail.inputComment" placeholder="학생에게 전달할 피드백" />
          </el-col>
        </el-row>
      </div>

      <template #footer>
        <el-button @click="essayGradeVisible = false">취소</el-button>
        <el-button type="primary" :loading="grading" @click="handleGradeEssay">
          채점 완료
        </el-button>
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
