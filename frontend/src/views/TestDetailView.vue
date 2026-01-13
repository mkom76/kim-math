<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { testAPI, submissionAPI, type Test, type Submission } from '../api/client'

const route = useRoute()
const router = useRouter()
const testId = route.params.id as string

const loading = ref(false)
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

          <el-table-column label="학생 정보" min-width="250">
            <template #default="{ row }">
              <div style="font-size: 13px; color: #606266">
                <div>🏫 {{ row.student?.school || '-' }}</div>
                <div style="margin-top: 4px">📚 {{ row.student?.grade || '-' }}</div>
                <div style="margin-top: 4px">🏢 {{ row.student?.academy || '-' }}</div>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="submissions.length === 0" description="아직 응시한 학생이 없습니다" />
      </el-card>
    </div>
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
