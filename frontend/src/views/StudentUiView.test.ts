import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StudentUiView from './StudentUiView.vue'

const routeState = vi.hoisted(() => ({ meta: { studentView: 'dashboard' } }))
vi.mock('vue-router', () => ({ useRoute: () => routeState }))
vi.mock('@/views/student-v2/StudentHomeView.vue', () => ({
  __esModule: true,
  default: { template: '<p>새 학생 홈</p>' },
}))
vi.mock('@/views/student-v2/StudentExamsView.vue', () => ({
  __esModule: true,
  default: { template: '<p>학생 시험</p>' },
}))
vi.mock('@/views/student-v2/StudentMoreView.vue', () => ({
  __esModule: true,
  default: { template: '<p>학생 더보기</p>' },
}))
vi.mock('@/views/student-v2/StudentStatisticsView.vue', () => ({
  __esModule: true,
  default: { template: '<p>학생 통계</p>' },
}))

describe('single student UI', () => {
  beforeEach(() => {
    localStorage.setItem('kim-math:student-ui-mode:101', 'legacy')
  })
  it.each([
    ['dashboard', '새 학생 홈'],
    ['exams', '학생 시험'],
    ['stats', '학생 더보기'],
    ['more', '학생 더보기'],
    ['testStats', '학생 통계'],
    ['homeworkStats', '학생 통계'],
    ['attendanceStats', '학생 통계'],
    ['videoStats', '학생 통계'],
  ])('opens the current %s view even with a saved V1 preference', async (key, text) => {
    routeState.meta.studentView = key
    const wrapper = mount(StudentUiView)
    await flushPromises()
    expect(wrapper.text()).toBe(text)
    wrapper.unmount()
  })
})
