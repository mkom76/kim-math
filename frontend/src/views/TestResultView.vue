<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submissionAPI, type SubmissionResult, type SubmissionDetail } from '@/api/client'
import { groupByTopic } from '@/components/topicStats'

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
  } catch (e: any) {
    if (e?.response?.status === 404) {
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
  if (ratio >= 0.8) return '#67c23a'
  if (ratio >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

const ratioTagType = (ratio: number): 'success' | 'warning' | 'danger' => {
  if (ratio >= 0.8) return 'success'
  if (ratio >= 0.5) return 'warning'
  return 'danger'
}

const correctCount = computed(() =>
  sortedDetails.value.filter(d => d.isCorrect === true).length,
)
const wrongCount = computed(() =>
  sortedDetails.value.filter(d => d.isCorrect === false).length,
)
const ungradedCount = computed(() =>
  sortedDetails.value.filter(d => d.isCorrect == null).length,
)

const typeLabel = (t?: string) =>
  ({ OBJECTIVE: '객관식', SUBJECTIVE: '주관식', ESSAY: '서술형' } as any)[t ?? ''] ?? ''

onMounted(fetch)
</script>

<template>
  <div v-loading="loading" style="padding: 16px; max-width: 960px; margin: 0 auto">
    <el-card shadow="never" style="margin-bottom: 12px">
      <div style="display: flex; align-items: center; gap: 12px">
        <el-button @click="goBack" :icon="ArrowLeft" />
        <div style="flex: 1; min-width: 0">
          <div style="font-size: 12px; color: #909399">시험 결과</div>
          <div style="font-size: 18px; font-weight: 600; line-height: 1.4">
            {{ result?.testTitle || '...' }}
          </div>
          <div v-if="!isStudent && result?.student" style="font-size: 13px; color: #606266; margin-top: 2px">
            {{ result.student.name }} ({{ result.student.grade }})
          </div>
        </div>
        <div style="text-align: right">
          <div style="font-size: 12px; color: #909399">총점</div>
          <div style="font-size: 22px; font-weight: 700; color: #409eff">
            {{ result?.totalScore ?? '-' }}<span style="font-size: 13px; color: #909399; margin-left: 2px">점</span>
          </div>
          <div v-if="result?.rank" style="font-size: 12px; color: #606266">
            반 {{ result.rank }}등
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-if="result" shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
          <span style="font-weight: 600">유형별 정답률</span>
          <el-tag size="small" type="success" effect="plain">정답 {{ correctCount }}</el-tag>
          <el-tag size="small" type="danger" effect="plain">오답 {{ wrongCount }}</el-tag>
          <el-tag v-if="ungradedCount > 0" size="small" type="info" effect="plain">미채점 {{ ungradedCount }}</el-tag>
        </div>
      </template>

      <el-empty v-if="topicStats.length === 0" description="채점된 문제가 없습니다" :image-size="80" />
      <div v-else style="display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px">
        <div
          v-for="s in topicStats"
          :key="s.topic"
          style="border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; background: #fafbfc"
        >
          <div style="display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 6px">
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
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            {{ s.correct }} / {{ s.total }} 정답
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-if="result" shadow="never">
      <template #header>
        <span style="font-weight: 600">문제별 답안</span>
      </template>
      <el-table :data="sortedDetails" stripe>
        <el-table-column prop="questionNumber" label="번호" width="64" align="center" />
        <el-table-column label="유형" width="120">
          <template #default="{ row }">
            <span v-if="row.topic" style="color: #409eff">{{ row.topic }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="형식" width="80" align="center">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #606266">{{ typeLabel(row.questionType) }}</span>
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
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="정답" min-width="100">
          <template #default="{ row }">
            <span v-if="row.correctAnswer" style="color: #67c23a">{{ row.correctAnswer }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="해설" width="80" align="center">
          <template #default="{ row }">
            <a
              v-if="row.videoLink"
              :href="row.videoLink"
              target="_blank"
              rel="noopener"
              style="color: #409eff; text-decoration: none"
            >▶ 보기</a>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
