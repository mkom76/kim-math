<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useStudentUiMode } from '@/composables/useStudentUiMode'

const route = useRoute()
const { mode } = useStudentUiMode()

const legacyStudentStatsView = defineAsyncComponent(() => import('@/views/StudentDetailView.vue'))
const previewMoreView = defineAsyncComponent(() => import('@/views/student-v2/StudentMoreView.vue'))
const previewStudentStatisticsView = defineAsyncComponent(() => import('@/views/student-v2/StudentStatisticsView.vue'))

const legacyViews = {
  dashboard: defineAsyncComponent(() => import('@/views/StudentDashboardView.vue')),
  exams: defineAsyncComponent(() => import('@/views/StudentDashboardView.vue')),
  testTaking: defineAsyncComponent(() => import('@/views/StudentTestTakingView.vue')),
  testResult: defineAsyncComponent(() => import('@/views/TestResultView.vue')),
  dailyFeedback: defineAsyncComponent(() => import('@/views/StudentDailyFeedbackView.vue')),
  stats: legacyStudentStatsView,
  more: legacyStudentStatsView,
  testStats: legacyStudentStatsView,
  homeworkStats: legacyStudentStatsView,
  attendanceStats: legacyStudentStatsView,
  videoStats: legacyStudentStatsView,
  clinic: defineAsyncComponent(() => import('@/views/StudentClinicView.vue')),
  videos: defineAsyncComponent(() => import('@/views/StudentVideosView.vue')),
} as const

const previewViews = {
  dashboard: defineAsyncComponent(() => import('@/views/student-v2/StudentHomeView.vue')),
  exams: defineAsyncComponent(() => import('@/views/student-v2/StudentExamsView.vue')),
  testTaking: defineAsyncComponent(() => import('@/views/student-v2/StudentTestTakingView.vue')),
  testResult: defineAsyncComponent(() => import('@/views/student-v2/TestResultView.vue')),
  dailyFeedback: defineAsyncComponent(() => import('@/views/student-v2/StudentDailyFeedbackView.vue')),
  stats: previewMoreView,
  more: previewMoreView,
  testStats: previewStudentStatisticsView,
  homeworkStats: previewStudentStatisticsView,
  attendanceStats: previewStudentStatisticsView,
  videoStats: previewStudentStatisticsView,
  clinic: defineAsyncComponent(() => import('@/views/student-v2/StudentClinicView.vue')),
  videos: defineAsyncComponent(() => import('@/views/student-v2/StudentVideosView.vue')),
} as const

type StudentViewKey = keyof typeof legacyViews

const viewKey = computed(() => route.meta.studentView as StudentViewKey)
const activeView = computed(() => mode.value === 'v2'
  ? previewViews[viewKey.value]
  : legacyViews[viewKey.value])
</script>

<template>
  <component :is="activeView" :key="`${mode}:${viewKey}`" />
</template>
