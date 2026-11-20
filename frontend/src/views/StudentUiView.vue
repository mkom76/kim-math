<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const studentMoreView = defineAsyncComponent(() => import('@/views/student-v2/StudentMoreView.vue'))
const studentStatisticsView = defineAsyncComponent(
  () => import('@/views/student-v2/StudentStatisticsView.vue'),
)

const studentViews = {
  dashboard: defineAsyncComponent(() => import('@/views/student-v2/StudentHomeView.vue')),
  exams: defineAsyncComponent(() => import('@/views/student-v2/StudentExamsView.vue')),
  testTaking: defineAsyncComponent(() => import('@/views/student-v2/StudentTestTakingView.vue')),
  testResult: defineAsyncComponent(() => import('@/views/student-v2/TestResultView.vue')),
  dailyFeedback: defineAsyncComponent(
    () => import('@/views/student-v2/StudentDailyFeedbackView.vue'),
  ),
  stats: studentMoreView,
  more: studentMoreView,
  testStats: studentStatisticsView,
  homeworkStats: studentStatisticsView,
  attendanceStats: studentStatisticsView,
  videoStats: studentStatisticsView,
  clinic: defineAsyncComponent(() => import('@/views/student-v2/StudentClinicView.vue')),
  videos: defineAsyncComponent(() => import('@/views/student-v2/StudentVideosView.vue')),
} as const

type StudentViewKey = keyof typeof studentViews

const viewKey = computed(() => route.meta.studentView as StudentViewKey)
const activeView = computed(() => studentViews[viewKey.value])
</script>

<template>
  <component :is="activeView" :key="viewKey" />
</template>
