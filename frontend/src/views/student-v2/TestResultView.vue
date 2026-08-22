<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submissionAPI, type SubmissionResult, type SubmissionDetail } from '@/api/client'
import { groupByTopic } from '@/components/topicStats'
import { useBreakpoint } from '@/composables/useBreakpoint'

const { isMobile } = useBreakpoint()

const route = useRoute()
const router = useRouter()

const isStudent = computed(() => route.path.startsWith('/student/'))
const testId = computed(() => Number(route.params.id ?? route.params.testId))
const studentId = computed(() =>
  route.params.studentId != null ? Number(route.params.studentId) : null,
)

const loading = ref(false)
const result = ref<SubmissionResult | null>(null)

const fetch = async () => {
  loading.value = true
  try {
    const res = isStudent.value
      ? await submissionAPI.getMyResultByTest(testId.value)
      : await submissionAPI.getResultByStudentAndTest(studentId.value!, testId.value)
    result.value = res.data
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 404) {
      ElMessage.warning('응시 기록이 없습니다')
    } else {
      ElMessage.error('결과를 불러오지 못했습니다')
    }
    goBack()
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  if (isStudent.value) router.push('/student/dashboard')
  else router.push(`/tests/${testId.value}`)
}

const sortedDetails = computed<SubmissionDetail[]>(() =>
  [...(result.value?.details ?? [])].sort((a, b) => a.questionNumber - b.questionNumber),
)

const topicStats = computed(() => groupByTopic(sortedDetails.value))

const ratioColor = (ratio: number): string => {
  if (ratio >= 0.8) return 'var(--student-success)'
  if (ratio >= 0.5) return 'var(--student-warning)'
  return 'var(--student-danger)'
}

const ratioTagType = (ratio: number): 'success' | 'warning' | 'danger' => {
  if (ratio >= 0.8) return 'success'
  if (ratio >= 0.5) return 'warning'
  return 'danger'
}

const correctCount = computed(() => sortedDetails.value.filter((d) => d.isCorrect === true).length)
const wrongCount = computed(() => sortedDetails.value.filter((d) => d.isCorrect === false).length)
const ungradedCount = computed(() => sortedDetails.value.filter((d) => d.isCorrect == null).length)

const typeLabel = (t?: string) =>
  (({ OBJECTIVE: '객관식', SUBJECTIVE: '주관식', ESSAY: '서술형' }) as Record<string, string>)[
    t ?? ''
  ] ?? ''

onMounted(fetch)
</script>

<template>
  <div
    v-loading="loading"
    :class="isStudent ? 'student-page student-page--wide result-page' : 'teacher-result-page'"
  >
    <el-card class="result-summary" shadow="never" style="margin-bottom: 12px">
      <div style="display: flex; align-items: center; gap: 12px">
        <el-button
          class="result-back"
          aria-label="이전 화면으로"
          @click="goBack"
          :icon="ArrowLeft"
        />
        <div style="flex: 1; min-width: 0">
          <div class="result-summary__eyebrow">시험 결과</div>
          <h1 class="result-summary__title">
            {{ result?.testTitle || '...' }}
          </h1>
          <div
            v-if="!isStudent && result?.student"
            style="font-size: 13px; color: var(--student-text); margin-top: 2px"
          >
            {{ result.student.name }} ({{ result.student.grade }})
          </div>
        </div>
        <div style="text-align: right">
          <template v-if="result && result.totalScore == null">
            <el-tag type="info" effect="plain" size="large">성적 비공개</el-tag>
            <div
              style="
                margin-top: 4px;
                font-size: 11px;
                color: var(--student-muted);
                max-width: 140px;
                line-height: 1.3;
              "
            >
              점수·등수는 가려졌지만 유형별 분석·정답·해설은 그대로 볼 수 있어요
            </div>
          </template>
          <template v-else>
            <div style="font-size: 12px; color: var(--student-muted)">총점</div>
            <div style="font-size: 22px; font-weight: 700; color: var(--student-primary)">
              {{ result?.totalScore ?? '-'
              }}<span style="font-size: 13px; color: var(--student-muted); margin-left: 2px"
                >점</span
              >
            </div>
            <div v-if="result?.rank" style="font-size: 12px; color: var(--student-text)">
              반 {{ result.rank }}등
            </div>
          </template>
        </div>
      </div>
    </el-card>

    <el-card v-if="result" class="result-topics" shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
          <span style="font-weight: 600">유형별 정답률</span>
          <el-tag size="small" type="success" effect="plain">정답 {{ correctCount }}</el-tag>
          <el-tag size="small" type="danger" effect="plain">오답 {{ wrongCount }}</el-tag>
          <el-tag v-if="ungradedCount > 0" size="small" type="info" effect="plain"
            >미채점 {{ ungradedCount }}</el-tag
          >
        </div>
      </template>

      <el-empty
        v-if="topicStats.length === 0"
        description="채점된 문제가 없습니다"
        :image-size="80"
      />
      <div
        v-else
        style="
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
          gap: 10px;
        "
      >
        <div v-for="s in topicStats" :key="s.topic" class="result-topic-card">
          <div
            style="
              display: flex;
              justify-content: space-between;
              align-items: baseline;
              margin-bottom: 6px;
            "
          >
            <span style="font-weight: 500">{{ s.topic }}</span>
            <el-tag :type="ratioTagType(s.ratio)" size="small">
              {{ Math.round(s.ratio * 100) }}%
            </el-tag>
          </div>
          <el-progress
            :percentage="Math.round(s.ratio * 100)"
            :color="ratioColor(s.ratio)"
            :show-text="false"
            :stroke-width="6"
          />
          <div style="font-size: 12px; color: var(--student-muted); margin-top: 4px">
            {{ s.correct }} / {{ s.total }} 정답
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-if="result" class="result-answers" shadow="never">
      <template #header>
        <span style="font-weight: 600">문제별 답안</span>
      </template>

      <!-- 모바일: 카드 리스트 -->
      <div v-if="isMobile" style="display: flex; flex-direction: column; gap: 10px">
        <div
          v-for="row in sortedDetails"
          :key="row.id"
          class="result-answer-card"
          :style="{
            border: '1px solid var(--student-border)',
            borderLeft:
              row.isCorrect === true
                ? '4px solid var(--student-success)'
                : row.isCorrect === false
                  ? '4px solid var(--student-danger)'
                  : '4px solid var(--student-muted)',
            borderRadius: '6px',
            padding: '12px',
            background: 'var(--student-surface)',
          }"
        >
          <div
            style="
              display: flex;
              align-items: center;
              gap: 8px;
              margin-bottom: 8px;
              flex-wrap: wrap;
            "
          >
            <span style="font-size: 18px; font-weight: 700; color: var(--student-ink)"
              >{{ row.questionNumber }}번</span
            >
            <el-tag v-if="row.isCorrect === true" type="success" size="small">정답</el-tag>
            <el-tag v-else-if="row.isCorrect === false" type="danger" size="small">오답</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">미채점</el-tag>
            <span
              v-if="row.topic"
              style="font-size: 12px; color: var(--student-primary); font-weight: 500"
            >
              {{ row.topic }}
            </span>
            <span style="margin-left: auto; font-size: 11px; color: var(--student-muted)">
              {{ typeLabel(row.questionType) }}
            </span>
          </div>

          <div
            style="display: grid; grid-template-columns: 60px 1fr; gap: 6px 10px; font-size: 14px"
          >
            <span style="color: var(--student-muted)">내 답</span>
            <span
              :style="{
                color: row.isCorrect === false ? 'var(--student-danger)' : 'var(--student-ink)',
                wordBreak: 'break-all',
                fontWeight: 500,
              }"
              >{{ row.studentAnswer || '—' }}</span
            >
            <template v-if="row.correctAnswer">
              <span style="color: var(--student-muted)">정답</span>
              <span
                style="color: var(--student-success); word-break: break-all; font-weight: 500"
                >{{ row.correctAnswer }}</span
              >
            </template>
          </div>

          <a
            v-if="row.videoLink"
            :href="row.videoLink"
            target="_blank"
            rel="noopener"
            class="result-video-link"
            style="
              display: inline-block;
              margin-top: 10px;
              padding: 6px 12px;
              background: var(--student-primary-soft);
              color: var(--student-primary);
              border-radius: 4px;
              font-size: 13px;
              text-decoration: none;
              font-weight: 500;
            "
            >▶ 해설 영상 보기</a
          >
        </div>
      </div>

      <!-- PC: 테이블 -->
      <el-table v-else :data="sortedDetails" stripe>
        <el-table-column prop="questionNumber" label="번호" width="64" align="center" />
        <el-table-column label="유형" width="120">
          <template #default="{ row }">
            <span v-if="row.topic" style="color: var(--student-primary)">{{ row.topic }}</span>
            <span v-else style="color: var(--student-text-disabled)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="형식" width="80" align="center">
          <template #default="{ row }">
            <span style="font-size: 12px; color: var(--student-text)">{{
              typeLabel(row.questionType)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="채점" width="76" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCorrect === true" type="success" size="small">정답</el-tag>
            <el-tag v-else-if="row.isCorrect === false" type="danger" size="small">오답</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">미채점</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="내 답" min-width="120">
          <template #default="{ row }">
            <span v-if="row.studentAnswer">{{ row.studentAnswer }}</span>
            <span v-else style="color: var(--student-text-disabled)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="정답" min-width="100">
          <template #default="{ row }">
            <span v-if="row.correctAnswer" style="color: var(--student-success)">{{
              row.correctAnswer
            }}</span>
            <span v-else style="color: var(--student-text-disabled)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="해설" width="80" align="center">
          <template #default="{ row }">
            <a
              v-if="row.videoLink"
              :href="row.videoLink"
              target="_blank"
              rel="noopener"
              style="color: var(--student-primary); text-decoration: none"
              >▶ 보기</a
            >
            <span v-else style="color: var(--student-text-disabled)">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.teacher-result-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}
.result-page {
  display: grid;
  gap: 16px;
  padding-bottom: 28px;
}
.result-page .result-summary,
.result-page .result-topics,
.result-page .result-answers {
  margin: 0 !important;
  border: 1px solid var(--student-border);
  border-radius: 19px;
  box-shadow: var(--student-shadow-sm);
}
.result-page .result-summary {
  overflow: hidden;
  border: 0;
  color: var(--student-surface);
  background: linear-gradient(135deg, var(--student-primary-strong), var(--student-blue-500));
  box-shadow: 0 16px 34px rgba(28, 68, 166, 0.2);
}
.result-page .result-summary :deep(.el-card__body) {
  padding: 20px;
}
.result-summary__eyebrow {
  color: rgba(255, 255, 255, 0.72);
  font-size: var(--student-font-sm);
}
.result-summary__title {
  margin: 0;
  color: var(--student-surface);
  font-size: 20px;
  font-weight: 800;
  line-height: 1.4;
}
.result-page .result-summary :deep(.el-button) {
  border: 1px solid rgba(255, 255, 255, 0.32);
  color: var(--student-surface);
  background: rgba(255, 255, 255, 0.12);
}
.result-page .result-summary :deep(.el-tag) {
  border-color: rgba(255, 255, 255, 0.5);
  color: var(--student-surface);
  background: rgba(255, 255, 255, 0.12);
}
.result-page .result-summary :deep(.el-card__body > div > div:nth-child(2) > div) {
  color: rgba(255, 255, 255, 0.72) !important;
}
.result-page .result-summary :deep(.el-card__body > div > div:nth-child(2) > div:nth-child(2)) {
  color: var(--student-surface) !important;
  font-size: 20px !important;
  font-weight: 800 !important;
}
.result-page .result-summary :deep(.el-card__body > div > div:last-child div) {
  color: rgba(255, 255, 255, 0.78) !important;
}
.result-page .result-summary :deep(.el-card__body > div > div:last-child div:nth-child(2)) {
  color: var(--student-surface) !important;
  font-size: 30px !important;
}
.result-page .result-topics :deep(.el-card__header),
.result-page .result-answers :deep(.el-card__header) {
  padding: 17px 18px;
  border-bottom-color: var(--student-border);
  color: var(--student-ink);
  font-size: 16px;
}
.result-page .result-topics :deep(.el-card__body),
.result-page .result-answers :deep(.el-card__body) {
  padding: 16px;
}
.result-page .result-topic-card {
  padding: 14px !important;
  border: 1px solid var(--student-border) !important;
  border-radius: 14px !important;
  background: var(--student-surface-soft) !important;
}
.result-page .result-answer-card {
  padding: 16px !important;
  border: 1px solid var(--student-border) !important;
  border-left-width: 5px !important;
  border-radius: 15px !important;
  box-shadow: 0 3px 12px rgba(31, 45, 73, 0.04);
}
.result-page .result-video-link {
  display: flex !important;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  margin-top: 13px !important;
  border-radius: 11px !important;
  background: var(--student-primary-soft) !important;
  color: var(--student-primary) !important;
  font-weight: 750 !important;
}
@media (max-width: 520px) {
  .result-page .result-summary :deep(.el-card__body) {
    padding: 17px;
  }
  .result-page .result-summary :deep(.el-card__body > div) {
    align-items: flex-start !important;
    gap: 10px !important;
  }
  .result-page .result-summary :deep(.el-card__body > div > div:last-child) {
    flex: 0 0 auto;
  }
}
</style>
