<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import {type Question, testAPI} from '../api/client'

const route = useRoute()
const router = useRouter()
const testId = route.params.id as string

const loading = ref(false)
const questions = ref<Question[]>([])
const hasChanges = ref(false)

const fetchQuestions = async () => {
  loading.value = true
  try {
    const response = await testAPI.getTestQuestions(Number(testId))
    questions.value = response.data.data || response.data
    // 번호 순으로 정렬
    questions.value.sort((a, b) => (a.number || 0) - (b.number || 0))
    reorderQuestions()
    hasChanges.value = false
  } catch (error) {
    ElMessage.error('문제 목록을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const handleAddQuestion = () => {
  const newNumber = questions.value.length + 1
  // 기본 배점 계산: 100점 / 문제 수
  const basePoints = Math.floor((100 / newNumber) * 10) / 10
  questions.value.push({
    number: newNumber,
    answer: '',
    points: basePoints
  })
  // 배점 재분배
  redistributePoints()
  hasChanges.value = true
  ElMessage.success('새 문제가 추가되었습니다.')
}

const redistributePoints = () => {
  const totalQuestions = questions.value.length
  if (totalQuestions === 0) return

  const basePoints = Math.floor((100 / totalQuestions) * 10) / 10
  let usedPoints = 0

  questions.value.forEach((q, index) => {
    if (index < totalQuestions - 1) {
      q.points = basePoints
      usedPoints += basePoints
    } else {
      // 마지막 문제에 남은 점수 할당
      q.points = Math.round((100 - usedPoints) * 10) / 10
    }
  })
}

const handleDeleteQuestion = async (question: Question, index: number) => {
  try {
    await ElMessageBox.confirm(
      `${question.number}번 문제를 삭제하시겠습니까?`,
      '삭제 확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    questions.value.splice(index, 1)
    reorderQuestions()
    redistributePoints()
    hasChanges.value = true
    ElMessage.success('문제가 제거되었습니다.')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('삭제에 실패했습니다.')
    }
  }
}

const reorderQuestions = () => {
  questions.value.forEach((q, index) => {
    q.number = index + 1
  })
}

const handleSaveAll = async () => {
  try {
    loading.value = true

    // 기존 문제 모두 삭제
    const existingQuestions = questions.value.filter(q => q.id)
    for (const question of existingQuestions) {
      if (question.id) {
        await testAPI.deleteQuestion(question.id)
      }
    }

    // 새로운 순서로 모두 추가
    for (const question of questions.value) {
      if (question.answer && question.answer.trim() !== '') {
        await testAPI.addQuestion(Number(testId), {
          number: question.number!,
          answer: question.answer,
          points: question.points
        })
      }
    }

    ElMessage.success('모든 정답이 저장되었습니다.')
    hasChanges.value = false
    await fetchQuestions()
  } catch (error) {
    ElMessage.error('저장에 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const handleAnswerChange = () => {
  hasChanges.value = true
}

const goBack = () => {
  if (hasChanges.value) {
    ElMessageBox.confirm(
      '저장하지 않은 변경사항이 있습니다. 정말 나가시겠습니까?',
      '확인',
      {
        confirmButtonText: '나가기',
        cancelButtonText: '취소',
        type: 'warning',
      }
    ).then(() => {
      router.push('/tests')
    }).catch(() => {})
  } else {
    router.push('/tests')
  }
}

onMounted(() => {
  fetchQuestions()
})
</script>

<template>
  <div class="teacher-view">
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#e6a23c">
              <EditPen />
            </el-icon>
            정답 관리
          </h1>
        </div>
        <div style="display: flex; gap: 12px">
          <el-button type="primary" @click="handleAddQuestion">
            <el-icon><Plus /></el-icon>
            문제 추가
          </el-button>
          <el-button @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            목록으로
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Questions List -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon color="#409eff">
            <List />
          </el-icon>
          <span style="font-weight: 600">문제 목록</span>
        </div>
      </template>

      <el-table
        :data="questions"
        v-loading="loading"
        style="width: 100%"
        stripe
        row-key="number"
      >
        <el-table-column prop="number" label="문제 번호" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="large">{{ row.number }}번</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="answer" label="정답" min-width="200">
          <template #default="{ row }">
            <el-input
              v-model="row.answer"
              placeholder="정답을 입력하세요"
              @input="handleAnswerChange"
            >
              <template #prepend>
                <el-icon color="#67c23a">
                  <Check />
                </el-icon>
              </template>
            </el-input>
          </template>
        </el-table-column>

        <el-table-column prop="points" label="배점" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.points"
              :min="0"
              :max="100"
              :precision="1"
              :step="0.1"
              @change="handleAnswerChange"
              style="width: 100%"
            >
              <template #suffix>점</template>
            </el-input-number>
          </template>
        </el-table-column>

        <el-table-column label="작업" width="80" align="center">
          <template #default="{ row, $index }">
            <el-button
              size="small"
              type="danger"
              circle
              @click="handleDeleteQuestion(row, $index)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="questions.length === 0" description="등록된 문제가 없습니다">
        <template #image>
          <el-icon size="60" color="#c0c4cc">
            <Document />
          </el-icon>
        </template>
      </el-empty>
    </el-card>

    <!-- Save Button -->
    <el-card shadow="never" v-if="questions.length > 0">
      <div style="display: flex; justify-content: center; gap: 16px; align-items: center">
        <el-alert
          v-if="hasChanges"
          title="저장하지 않은 변경사항이 있습니다"
          type="warning"
          :closable="false"
          show-icon
          style="flex: 1"
        />
        <el-button
          type="success"
          size="large"
          @click="handleSaveAll"
          :disabled="!hasChanges"
          :loading="loading"
          style="min-width: 200px"
        >
          <el-icon><Check /></el-icon>
          전체 저장
        </el-button>
      </div>
    </el-card>
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
