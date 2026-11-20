import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import StudentHomeView from './StudentHomeView.vue'

const api = vi.hoisted(() => ({
  user: vi.fn(),
  student: vi.fn(),
  submissions: vi.fn(),
  homework: vi.fn(),
  attendance: vi.fn(),
  unread: vi.fn(),
}))
vi.mock('@/api/client', () => ({
  authAPI: { getCurrentUser: api.user },
  studentAPI: { getStudent: api.student },
  submissionAPI: { getStudentSubmissions: api.submissions },
  studentHomeworkAPI: { getByStudentId: api.homework },
  lessonAPI: { getAttendanceStats: api.attendance },
  studentNotificationAPI: { getUnreadCount: api.unread },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))

const wrappers: ReturnType<typeof mount>[] = []
function mountHome() {
  const wrapper = mount(StudentHomeView, {
    global: {
      directives: { loading: {} },
      stubs: {
        StudentClassSwitcher: true,
        StudentUiGuideDialog: true,
        ElIcon: { template: '<span><slot /></span>' },
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('student home latest exam', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.user.mockResolvedValue({ data: { userId: 1, name: '예시 학생' } })
    api.student.mockResolvedValue({ data: { academyName: '샘플 학원' } })
    api.submissions.mockResolvedValue({ data: [] })
    api.homework.mockResolvedValue({ data: [] })
    api.attendance.mockResolvedValue(null)
    api.unread.mockResolvedValue({ data: { count: 0 } })
  })
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.restoreAllMocks()
  })

  it('shows the newest submitted exam rather than the highest score or response order, including zero', async () => {
    api.submissions.mockResolvedValue({
      data: {
        content: [
          { id: 9, testTitle: '지난 시험', totalScore: 100, submittedAt: '2026-11-15T10:00:00' },
          { id: 2, testTitle: '가장 최근 시험', totalScore: 0, submittedAt: '2026-11-20T10:00:00' },
          { id: 3, testTitle: '날짜 미상', totalScore: 90, submittedAt: 'invalid' },
        ],
      },
    })
    const wrapper = mountHome()
    await flushPromises()
    const latest = wrapper.get('[data-testid="latest-test"]')
    expect(latest.text()).toContain('가장 최근 시험')
    expect(latest.text()).toContain('0점')
    expect(latest.text()).not.toContain('100점')
    expect(wrapper.findAll('.dashboard-shortcut').map((link) => link.attributes('href'))).toEqual([
      '/student/videos',
      '/student/statistics/tests',
      '/student/solution-notebook',
    ])
  })

  it('keeps a hidden latest score hidden without substituting an older visible score', async () => {
    api.submissions.mockResolvedValue({
      data: [
        { id: 1, testTitle: '이전 시험', totalScore: 80, submittedAt: '2026-11-15' },
        { id: 2, testTitle: '비공개 시험', totalScore: null, submittedAt: '2026-11-20' },
      ],
    })
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.get('[data-testid="latest-test"]').text()).toContain('비공개 시험')
    expect(wrapper.get('.statistics-value').text()).toBe('비공개')
    expect(wrapper.text()).not.toContain('80점')
  })

  it('shows an explicit empty state and no fabricated statistics', async () => {
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.get('[data-testid="latest-test"]').text()).toContain('응시 기록이 없어요')
    expect(wrapper.findAll('.statistics-value').map((row) => row.text())).toEqual(['-', '-', '-'])
  })

  it('uses the creation time when submission time is absent and reports pending grading', async () => {
    api.submissions.mockResolvedValue({
      data: [
        { id: 1, testTitle: '이전 시험', totalScore: 80, submittedAt: '2026-11-15' },
        {
          id: 2,
          test: { title: '최근 서술형 시험' },
          totalScore: null,
          pendingEssayCount: 1,
          createdAt: '2026-11-20',
        },
      ],
    })
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.get('[data-testid="latest-test"]').text()).toContain('최근 서술형 시험')
    expect(wrapper.get('.statistics-value').text()).toBe('채점 중')
  })

  it('shows the latest lesson homework and its zero completion instead of the average', async () => {
    api.homework.mockResolvedValue({
      data: [
        {
          id: 9,
          homeworkTitle: '지난 숙제',
          completion: 100,
          lessonDate: '2026-11-15',
          updatedAt: '2026-11-21',
        },
        { id: 2, homeworkTitle: '최근 숙제', completion: 0, lessonDate: '2026-11-20' },
      ],
    })
    const wrapper = mountHome()
    await flushPromises()
    const homework = wrapper.get('[data-testid="latest-homework"]')
    expect(homework.text()).toContain('최근 숙제')
    expect(homework.get('.statistics-value').text()).toBe('0%')
    expect(homework.text()).not.toContain('지난 숙제')
  })

  it('keeps missing latest completion empty rather than using an older homework', async () => {
    api.homework.mockResolvedValue({
      data: {
        content: [
          { id: 1, homeworkTitle: '이전 숙제', completion: 80, createdAt: '2026-11-15' },
          { id: 2, homeworkTitle: '새 숙제', completion: null, createdAt: '2026-11-20' },
        ],
      },
    })
    const wrapper = mountHome()
    await flushPromises()
    const homework = wrapper.get('[data-testid="latest-homework"]')
    expect(homework.text()).toContain('새 숙제')
    expect(homework.get('.statistics-value').text()).toBe('-')
  })

  it('offers a working retry after a failed load', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    api.submissions.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('학습 정보를 불러오지 못했어요')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.submissions).toHaveBeenCalledTimes(2)
  })
})
