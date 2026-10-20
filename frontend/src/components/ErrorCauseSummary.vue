<script setup lang="ts">
import type { ErrorCauses } from '@/api/client'
import { ERROR_CAUSES, errorCauseStatus } from '@/utils/errorCauses'
defineProps<{ value?: ErrorCauses | null }>()
</script>

<template>
  <section class="cause-summary" aria-label="이번 시험 오답 원인">
    <div class="cause-summary__heading">
      <h3>오답 원인</h3>
      <span>{{ errorCauseStatus(value) }}</span>
    </div>
    <p v-if="!value || value.status === 'NOT_ENTERED'" class="cause-summary__note">
      선생님이 원인을 입력하면 여기에 표시됩니다.
    </p>
    <template v-else>
      <p v-if="value.status === 'NEEDS_REVIEW'" class="cause-summary__review">
        답안 또는 채점 변경 후 선생님의 재확인이 필요합니다.
      </p>
      <dl>
        <div v-for="cause in ERROR_CAUSES" :key="cause.key">
          <dt>{{ cause.label }}</dt>
          <dd>{{ value[cause.key] == null ? '미입력' : `${value[cause.key]}건` }}</dd>
        </div>
      </dl>
      <p class="cause-summary__note">
        원인별 기록 건수입니다. 한 문항에 여러 원인이 포함될 수 있습니다.
      </p>
    </template>
  </section>
</template>

<style scoped>
.cause-summary {
  color: var(--student-ink, #303133);
}
.cause-summary__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h3 {
  margin: 0;
  font-size: 16px;
}
.cause-summary__heading span,
.cause-summary__note {
  color: var(--student-muted, #606266);
  font-size: 13px;
  line-height: 1.6;
}
dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(125px, 1fr));
  gap: 12px;
  margin: 16px 0;
}
dl div {
  padding: 12px;
  border: 1px solid var(--student-border, #dcdfe6);
  border-radius: 8px;
}
dt {
  font-size: 13px;
}
dd {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 600;
}
.cause-summary__review {
  color: #9a3412;
  font-size: 13px;
}
.cause-summary__note {
  margin-bottom: 0;
}
</style>
