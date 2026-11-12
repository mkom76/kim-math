<script setup lang="ts">
import type { NotebookVerification } from '@/api/solutionNotebook'
defineProps<{ report: NotebookVerification | null }>()
const categories: Record<string, string> = {
  SIGN: '부호',
  FRACTION: '분수',
  EXPONENT_ROOT: '지수·루트',
  CONDITION: '문제 조건',
  CALCULATION: '계산',
  ANSWER: '정답',
  OMISSION: '누락',
  EXTRA_CONTENT: '추가된 내용',
  DIAGRAM: '그림',
  UNREADABLE: '판독 어려움',
  OTHER: '기타',
}
const verdicts = {
  MISMATCH: '비교 중 차이를 발견했어요',
  UNCERTAIN: '정확하게 판단하기 어려워요',
  INVALID_RESPONSE: '검증 응답 형식을 확인하지 못했어요',
  UNAVAILABLE: '검증 서비스의 응답을 받지 못했어요',
  PASSED: '검증 통과',
}
</script>
<template>
  <section
    v-if="report && report.verdict !== 'PASSED'"
    class="verification-panel"
    aria-label="검증 상세"
  >
    <h3>{{ report.stage === 'IMAGE' ? '손글씨 이미지' : '문제·풀이' }} 검증 내용</h3>
    <p class="verification-verdict">{{ verdicts[report.verdict] }}</p>
    <p class="verification-help">AI의 비교 결과이며, 선생님의 확인이 필요할 수 있어요.</p>
    <ol v-if="report.issues.length">
      <li v-for="(issue, i) in report.issues" :key="i">
        <strong>{{ categories[issue.category] || '기타' }} · {{ issue.location }}</strong>
        <dl>
          <dt>기준 내용</dt>
          <dd>{{ issue.expected || '기준을 특정하지 못했어요.' }}</dd>
          <dt>비교 대상에서 읽힌 내용</dt>
          <dd>{{ issue.observed || '선명하게 읽지 못했어요.' }}</dd>
        </dl>
        <p>{{ issue.reason }}</p>
      </li>
    </ol>
    <p v-else>구체적인 수식 차이는 확인되지 않았어요. 수식 오류로 확정한 결과는 아닙니다.</p>
    <p class="verification-help">
      자동으로 다시 생성하지 않습니다. 이 내용은 현재 결과와 함께 임시로 표시됩니다.
    </p>
  </section>
</template>
<style scoped>
.verification-panel {
  padding: 18px;
  border: 1px solid #e5cc9b;
  border-radius: 12px;
  background: #fffbf2;
  color: #57462e;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  min-width: 0;
}
h3 {
  margin: 0;
  font-size: 16px;
}
.verification-verdict {
  font-weight: 600;
  margin: 10px 0;
}
.verification-help {
  color: #75654f;
  font-size: 12px;
}
ol {
  padding-left: 22px;
}
li {
  padding: 12px 0;
  border-top: 1px solid #eee1c8;
}
dl {
  margin: 10px 0;
  display: grid;
  gap: 4px;
}
dt {
  font-size: 12px;
  color: #75654f;
}
dd {
  margin: 0 0 8px;
  white-space: pre-wrap;
  font-family: inherit;
}
li p {
  white-space: pre-wrap;
  margin-bottom: 0;
}
</style>
