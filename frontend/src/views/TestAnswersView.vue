<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import {type Question, testAPI, type TextbookProblem} from '../api/client'
import TextbookProblemPicker from '@/components/TextbookProblemPicker.vue'

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
    points: basePoints,
    questionType: 'OBJECTIVE'
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

    // 일괄 저장 API 사용 (학생 답안을 삭제하지 않고 업데이트)
    const answersToSave = questions.value.map(q => ({
      number: q.number!,
      answer: q.answer || '',
      points: q.points || 0,
      questionType: q.questionType || 'SUBJECTIVE'
    }))

    await testAPI.saveTestAnswers(Number(testId), answersToSave)

    ElMessage.success('모든 정답이 저장되었습니다.')
    hasChanges.value = false
    await fetchQuestions()
  } catch (error) {
    ElMessage.error('저장에 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const totalPoints = computed(() => {
  return Math.round(questions.value.reduce((sum, q) => sum + (q.points || 0), 0) * 10) / 10
})

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

// 교재 picker
const pickerVisible = ref(false)

const usedTextbookProblemIds = computed(() =>
  questions.value.map(q => q.textbookProblem?.id).filter((x): x is number => x != null),
)

const openPicker = async () => {
  if (hasChanges.value) {
    try {
      await ElMessageBox.confirm(
        '저장하지 않은 변경사항이 있습니다. 먼저 저장하면 현재 문제 수정 내용이 반영됩니다. 그대로 교재에서 가져올까요?',
        '확인',
        { confirmButtonText: '계속', cancelButtonText: '취소', type: 'warning' },
      )
    } catch {
      return
    }
  }
  pickerVisible.value = true
}

const onPick = async (items: { problem: TextbookProblem }[]) => {
  const startNumber = (questions.value.length > 0
    ? Math.max(...questions.value.map(q => q.number || 0))
    : 0) + 1
  const defaultPoints = 5
  const payload = items.map((it, idx) => ({
    textbookProblemId: it.problem.id!,
    number: startNumber + idx,
    points: defaultPoints,
  }))
  try {
    await testAPI.addQuestionsFromTextbook(Number(testId), payload)
    ElMessage.success(`${items.length}개 문제를 추가했습니다`)
    await fetchQuestions()
  } catch {
    ElMessage.error('교재 문제 추가에 실패했습니다')
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
          <el-button type="primary" plain @click="openPicker">
            <el-icon><Collection /></el-icon>
            교재에서 가져오기
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
        <el-table-column prop="number" label="문제 번호" width="160" align="center">
          <template #default="{ row }">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 4px">
              <el-tag type="info" size="large">{{ row.number }}번</el-tag>
              <el-tag v-if="row.textbookProblem" type="success" size="small" effect="plain">
                📚 교재 #{{ row.textbookProblem.number }}
              </el-tag>
              <span v-if="row.textbookProblem?.topic" style="font-size: 11px; color: #409eff">
                {{ row.textbookProblem.topic }}
              </span>
              <a
                v-if="row.textbookProblem?.videoLink"
                :href="row.textbookProblem.videoLink"
                target="_blank"
                rel="noopener"
                style="font-size: 11px; color: #409eff; text-decoration: none"
              >▶ 해설</a>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="questionType" label="문제 유형" width="180" align="center">
          <template #default="{ row }">
            <el-select
              v-model="row.questionType"
              placeholder="유형 선택"
              @change="handleAnswerChange"
              style="width: 100%"
            >
              <el-option label="객관식" value="OBJECTIVE">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-icon><Select /></el-icon>
                  <span>객관식</span>
                </div>
              </el-option>
              <el-option label="주관식" value="SUBJECTIVE">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-icon><Edit /></el-icon>
                  <span>주관식</span>
                </div>
              </el-option>
              <el-option label="서술형" value="ESSAY">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-icon><Memo /></el-icon>
                  <span>서술형</span>
                </div>
              </el-option>
            </el-select>
          </template>
        </el-table-column>

        <el-table-column prop="answer" label="정답" min-width="200">
          <template #default="{ row }">
            <!-- 객관식: 라디오 버튼 -->
            <el-radio-group
              v-if="row.questionType === 'OBJECTIVE'"
              v-model="row.answer"
              @change="handleAnswerChange"
              style="width: 100%"
            >
              <el-radio-button label="1">1</el-radio-button>
              <el-radio-button label="2">2</el-radio-button>
              <el-radio-button label="3">3</el-radio-button>
              <el-radio-button label="4">4</el-radio-button>
              <el-radio-button label="5">5</el-radio-button>
            </el-radio-group>

            <!-- 주관식: 텍스트 입력 -->
            <el-input
              v-else-if="row.questionType === 'SUBJECTIVE'"
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

            <!-- 서술형: 자동채점 없음 -->
            <el-tag v-else type="info" effect="plain">선생님 직접 채점</el-tag>
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

      <div v-if="questions.length > 0" style="display: flex; justify-content: flex-end; align-items: center; margin-top: 16px; padding: 12px 16px; background: #f5f7fa; border-radius: 8px;">
        <span style="font-size: 16px; font-weight: 600; color: #303133;">
          배점 합계: {{ totalPoints }}점
        </span>
      </div>
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

    <TextbookProblemPicker
      v-model:visible="pickerVisible"
      :exclude-problem-ids="usedTextbookProblemIds"
      @pick="onPick"
    />
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
