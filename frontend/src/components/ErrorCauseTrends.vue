<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Submission } from '@/api/client'
import {
  ERROR_CAUSES,
  errorCauseDate,
  errorCauseDelta,
  errorCauseSegments,
  errorCauseStatus,
  sortErrorCauseRecords,
  type ErrorCauseKey,
} from '@/utils/errorCauses'

const props = defineProps<{ submissions: Submission[] }>()
const selectedClass = ref('')
const selectedCauses = ref<ErrorCauseKey[]>(ERROR_CAUSES.map((cause) => cause.key))
const sorted = computed(() => sortErrorCauseRecords(props.submissions))
const classes = computed(() => [
  ...new Map(
    sorted.value.map((item) => [
      String(item.classId ?? 'unknown'),
      { id: String(item.classId ?? 'unknown'), name: item.className || '시험 기록' },
    ]),
  ).values(),
])
watch(
  classes,
  (choices) => {
    if (!choices.some((item) => item.id === selectedClass.value))
      selectedClass.value = choices.at(-1)?.id ?? ''
  },
  { immediate: true },
)
const records = computed(() =>
  sorted.value.filter((item) => String(item.classId ?? 'unknown') === selectedClass.value),
)
const visibleCauses = computed(() =>
  ERROR_CAUSES.filter((cause) => selectedCauses.value.includes(cause.key)),
)
const series = computed(() =>
  visibleCauses.value.map((cause) => ({
    ...cause,
    segments: errorCauseSegments(records.value, cause.key),
  })),
)
const hasPoints = computed(() => series.value.some((item) => item.segments.length))
const width = computed(() => Math.max(640, records.value.length * 130 + 70))
const maximum = computed(() =>
  Math.max(
    1,
    ...series.value.flatMap((item) =>
      item.segments.flatMap((segment) => segment.map((point) => point.value)),
    ),
  ),
)
const ticks = computed(() => [...new Set([0, Math.ceil(maximum.value / 2), maximum.value])])
const x = (index: number) =>
  records.value.length === 1
    ? width.value / 2
    : 70 + (index * (width.value - 140)) / Math.max(1, records.value.length - 1)
const y = (value: number) => 185 - (value * 150) / maximum.value
const points = (segment: Array<{ index: number; value: number }>) =>
  segment.map((point) => `${x(point.index)},${y(point.value)}`).join(' ')

function toggle(key: ErrorCauseKey) {
  selectedCauses.value = selectedCauses.value.includes(key)
    ? selectedCauses.value.filter((item) => item !== key)
    : [...selectedCauses.value, key]
}

function deltaLabel(index: number, key: ErrorCauseKey) {
  if (index === 0) return '비교 없음'
  const delta = errorCauseDelta(records.value, index, key)
  return delta == null
    ? '비교 불가'
    : delta === 0
      ? '변화 없음'
      : `직전 대비 ${delta > 0 ? '+' : ''}${delta}건`
}

function dateLabel(item: Submission) {
  const date = errorCauseDate(item)
  return date ? `${date}${item.lessonDate ? '' : ' · 생성일 기준'}` : '날짜 미등록'
}
</script>

<template>
  <section class="cause-trends" aria-label="오답 원인별 변화">
    <div class="cause-trends__heading">
      <div>
        <h3>오답 원인별 변화</h3>
        <p>시험별 기록 건수를 비교해 보세요.</p>
      </div>
      <label v-if="classes.length > 1" class="cause-trends__class"
        >반 선택
        <select v-model="selectedClass">
          <option v-for="item in classes" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>
    </div>
    <p v-if="!records.length" class="cause-trends__empty">
      응시 기록이 쌓이면 오답 원인 변화를 확인할 수 있습니다.
    </p>
    <template v-else>
      <div class="cause-trends__legend" aria-label="그래프에 표시할 원인">
        <button
          v-for="cause in ERROR_CAUSES"
          :key="cause.key"
          type="button"
          :aria-pressed="selectedCauses.includes(cause.key)"
          @click="toggle(cause.key)"
        >
          <i :style="{ background: cause.color }" aria-hidden="true" />{{ cause.label }}
        </button>
      </div>
      <p class="cause-trends__scroll-hint">그래프와 표를 좌우로 밀어 볼 수 있습니다.</p>
      <div
        v-if="hasPoints"
        class="cause-trends__scroll"
        tabindex="0"
        aria-label="원인별 추이 그래프, 가로로 스크롤할 수 있습니다"
      >
        <svg
          :viewBox="`0 0 ${width} 250`"
          :style="{ minWidth: `${width}px` }"
          role="img"
          aria-label="시험별 오답 원인 건수. 상세 값은 아래 표에서 확인할 수 있습니다."
        >
          <g v-for="tick in ticks" :key="tick">
            <line
              x1="45"
              :y1="y(tick)"
              :x2="width - 25"
              :y2="y(tick)"
              stroke="#dcdfe6"
              stroke-dasharray="4,4"
            />
            <text x="35" :y="y(tick) + 4" text-anchor="end">{{ tick }}</text>
          </g>
          <text x="12" y="18">건</text>
          <g v-for="(record, index) in records" :key="record.id ?? record.testId ?? index">
            <text :x="x(index)" y="212" text-anchor="middle">
              {{ (record.testTitle || record.test?.title || '시험').slice(0, 12) }}
            </text>
            <text :x="x(index)" y="232" text-anchor="middle">
              {{ errorCauseDate(record) || '날짜 미등록' }}
            </text>
          </g>
          <g v-for="cause in series" :key="cause.key">
            <g v-for="(segment, index) in cause.segments" :key="index">
              <polyline
                v-if="segment.length > 1"
                :points="points(segment)"
                fill="none"
                :stroke="cause.color"
                stroke-width="2.5"
              />
              <circle
                v-for="point in segment"
                :key="point.index"
                :cx="x(point.index)"
                :cy="y(point.value)"
                r="4"
                :fill="cause.color"
              >
                <title>
                  {{ records[point.index]?.testTitle }} · {{ cause.label }} {{ point.value }}건
                </title>
              </circle>
            </g>
          </g>
        </svg>
      </div>
      <p v-else class="cause-trends__empty">
        {{
          selectedCauses.length
            ? '그래프로 표시할 원인 기록이 없습니다.'
            : '표시할 원인을 선택해 주세요.'
        }}
      </p>
      <p class="cause-trends__note">
        원인 중복 기록이 가능합니다. 미입력·재확인 필요 구간은 그래프와 증감 비교에서 제외합니다.
      </p>
      <div
        class="cause-trends__scroll"
        tabindex="0"
        aria-label="회차별 원인 건수 표, 가로로 스크롤할 수 있습니다"
      >
        <table>
          <caption>
            회차별 원인 기록과 직전 시험 대비 변화
          </caption>
          <thead>
            <tr>
              <th scope="col">시험</th>
              <th scope="col">등록 상태</th>
              <th v-for="cause in ERROR_CAUSES" :key="cause.key" scope="col">{{ cause.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(record, index) in records" :key="record.id ?? record.testId ?? index">
              <th scope="row">
                <strong>{{ record.testTitle || record.test?.title || '시험' }}</strong
                ><small>{{ dateLabel(record) }}</small>
              </th>
              <td>{{ errorCauseStatus(record.errorCauses) }}</td>
              <td v-for="cause in ERROR_CAUSES" :key="cause.key">
                <strong>{{
                  record.errorCauses?.[cause.key] == null
                    ? '미입력'
                    : `${record.errorCauses[cause.key]}건`
                }}</strong>
                <small>{{ deltaLabel(index, cause.key) }}</small>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.cause-trends {
  margin-top: 24px;
  padding: 20px;
  border: 1px solid var(--student-border, #dcdfe6);
  border-radius: 12px;
  background: var(--student-surface, #fff);
  color: var(--student-ink, #303133);
}
.cause-trends__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
h3 {
  margin: 0;
  font-size: 17px;
}
p {
  line-height: 1.6;
}
.cause-trends__heading p,
.cause-trends__note,
.cause-trends__empty {
  font-size: 13px;
  color: var(--student-muted, #606266);
}
.cause-trends__scroll-hint {
  display: none;
  font-size: 12px;
  color: var(--student-muted, #606266);
}
.cause-trends__class {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
select {
  min-height: 44px;
  max-width: 190px;
  padding: 8px;
  border: 1px solid #c0c4cc;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  font: inherit;
}
.cause-trends__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 16px;
}
button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid #c0c4cc;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
button[aria-pressed='false'] {
  opacity: 0.5;
}
button i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
button:focus-visible,
select:focus-visible,
.cause-trends__scroll:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 3px;
}
.cause-trends__scroll {
  overflow-x: auto;
}
svg {
  display: block;
  width: 100%;
  height: auto;
}
svg text {
  font-size: 11px;
  fill: var(--student-muted, #606266);
}
table {
  width: 100%;
  min-width: 800px;
  border-collapse: collapse;
  font-size: 13px;
}
caption {
  text-align: left;
  padding: 8px 0 12px;
  font-weight: 600;
}
th,
td {
  padding: 12px;
  border-bottom: 1px solid var(--student-border, #e4e7ed);
  text-align: left;
  vertical-align: top;
}
thead th {
  background: var(--student-surface-alt, #f5f7fa);
  white-space: nowrap;
}
tbody th {
  min-width: 140px;
}
td {
  min-width: 80px;
}
strong {
  font-weight: 500;
}
small {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--student-muted, #606266);
  font-weight: 400;
  white-space: nowrap;
}
@media (max-width: 600px) {
  .cause-trends {
    padding: 14px;
  }
  .cause-trends__scroll-hint {
    display: block;
  }
}
</style>
