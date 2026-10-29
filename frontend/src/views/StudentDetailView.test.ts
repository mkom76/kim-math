import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  authAPI, lessonAPI, studentAPI, studentHomeworkAPI, submissionAPI, testAPI, videoProgressAPI,
  type Student, type Submission,
} from '@/api/client'
import ErrorCauseTrends from '@/components/ErrorCauseTrends.vue'
import StudentDetailView from './StudentDetailView.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/api/client', () => ({
  authAPI: { getCurrentUser: vi.fn() },
  studentAPI: { getStudent: vi.fn() },
  studentHomeworkAPI: { getByStudentId: vi.fn() },
  submissionAPI: { getStudentSubmissions: vi.fn() },
  testAPI: { getTestStats: vi.fn() },
  videoProgressAPI: { getStudentProgress: vi.fn() },
  lessonAPI: { getAttendanceStats: vi.fn() },
}))

const student: Student = {
  id: 1, name: '학생', grade: '고1', school: '학교', classId: 1, className: '현재 반',
  enrollments: [
    { classId: 2, className: '이전 반', status: 'COMPLETED', endReason: 'CLASS_ENDED', canManage: false },
    { classId: 1, className: '현재 반', status: 'ACTIVE', canManage: true },
    { classId: 3, className: '새 반', status: 'ACTIVE', canManage: true },
  ],
}
const records: Submission[] = [
  { id: 1, testId: 1, testTitle: '현재 첫 시험', classId: 1, className: '현재 반',
    totalScore: 70, submittedAt: '2026-10-10T19:00:00' },
  { id: 2, testId: 2, testTitle: '현재 둘째 시험', classId: 1, className: '현재 반',
    totalScore: 90, submittedAt: '2026-10-12T19:00:00' },
  // A later answer correction must not make the ended class the default.
  { id: 3, testId: 3, testTitle: '종강반 시험', classId: 2, className: '이전 반',
    classEndedAt: '2026-09-30T19:00:00', totalScore: 10, submittedAt: '2026-10-20T19:00:00' },
]
const slotStub = { template: '<div><slot name="header"/><slot/></div>' }

async function mountDetail() {
  const wrapper = shallowMount(StudentDetailView, {
    global: {
      stubs: {
        ElCard: slotStub, ElTabs: slotStub, ElTabPane: slotStub,
        ElRow: slotStub, ElCol: slotStub, ElTag: slotStub,
        ElButton: { template: '<button><slot/></button>' },
        ElIcon: true, ElAvatar: true, ElSwitch: true, ElProgress: true, ElTableColumn: true,
        ElDialog: { props: ['modelValue', 'title'], template: '<section v-if="modelValue" role="dialog"><h2>{{ title }}</h2><slot/></section>' },
        ElTable: { name: 'ElTable', props: ['data'], template: '<div/>' },
        ElEmpty: { props: ['description'], template: '<p>{{ description }}</p>' },
        User: true, Document: true, Calendar: true, DataLine: true, VideoPlay: true,
        ChatLineSquare: true, InfoFilled: true,
      },
      directives: { loading: () => {} },
    },
  })
  await flushPromises()
  return wrapper
}

describe('student test statistics by class', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.innerWidth = 1024
    vi.mocked(authAPI.getCurrentUser).mockResolvedValue({ data: { role: 'TEACHER', userId: 9 } } as never)
    vi.mocked(studentAPI.getStudent).mockResolvedValue({ data: structuredClone(student) } as never)
    vi.mocked(submissionAPI.getStudentSubmissions).mockResolvedValue({ data: structuredClone(records) } as never)
    vi.mocked(studentHomeworkAPI.getByStudentId).mockResolvedValue({ data: [] } as never)
    vi.mocked(videoProgressAPI.getStudentProgress).mockResolvedValue({ data: [] } as never)
    vi.mocked(lessonAPI.getAttendanceStats).mockResolvedValue(null as never)
    vi.mocked(testAPI.getTestStats).mockResolvedValue({ data: { averageScore: 60 } } as never)
  })

  it('defaults to the current class and switches the graph, statistics, list and error causes together', async () => {
    const wrapper = await mountDetail()
    const select = wrapper.get('select[aria-label="시험 통계 반 선택"]')
    expect((select.element as HTMLSelectElement).value).toBe('1')
    expect(select.text()).toContain('이전 반 · 종강')
    expect(wrapper.text()).toMatch(/2\s*총 응시 시험/)
    expect(wrapper.text()).toMatch(/80\s*평균 점수/)
    expect(wrapper.text()).toMatch(/90\s*최고 점수/)
    expect(wrapper.text()).toMatch(/70\s*최저 점수/)
    expect(wrapper.find('svg').findAll('circle title').map(title => title.text())).toEqual([
      '70점 (평균: 60점)', '90점 (평균: 60점)',
    ])
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data').map((item: Submission) => item.testId)).toEqual([1, 2])
    expect(wrapper.findComponent(ErrorCauseTrends).props('submissions').map((item: Submission) => item.testId)).toEqual([1, 2])

    await select.setValue('2')
    expect(wrapper.text()).toMatch(/1\s*총 응시 시험/)
    expect(wrapper.text()).toMatch(/10\s*평균 점수/)
    expect(wrapper.find('svg').findAll('polyline')).toHaveLength(0)
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data').map((item: Submission) => item.testId)).toEqual([3])
    expect(wrapper.findComponent(ErrorCauseTrends).props('submissions').map((item: Submission) => item.testId)).toEqual([3])
    await wrapper.findAll('button').find(button => button.text() === '확대하여 보기')!.trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('점수 추이 상세 · 이전 반 · 종강')
    expect(wrapper.get('[role="dialog"]').findAll('circle title').map(title => title.text())).toEqual(['10점 (평균: 60점)'])
  })

  it('keeps an active class with no exams empty instead of displaying an ended class score', async () => {
    vi.mocked(studentAPI.getStudent).mockResolvedValue({ data: { ...student, classId: 3 } } as never)
    const wrapper = await mountDetail()
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('3')
    expect(wrapper.text()).toMatch(/0\s*총 응시 시험/)
    expect(wrapper.text()).toContain('선택한 반에서 응시한 시험이 없습니다')
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.findComponent(ErrorCauseTrends).props('submissions')).toEqual([])
  })

  it('preserves completed-only history and labels ended classes even without enrollment metadata', async () => {
    vi.mocked(studentAPI.getStudent).mockResolvedValue({ data: {
      ...student, classId: 2, enrollments: [],
    } } as never)
    vi.mocked(submissionAPI.getStudentSubmissions).mockResolvedValue({ data: [records[2]] } as never)
    const wrapper = await mountDetail()
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('2')
    expect(wrapper.get('select').text()).toContain('이전 반 · 종강')
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toHaveLength(1)
  })

  it('filters mobile exam cards by the same selection', async () => {
    window.innerWidth = 390
    const wrapper = await mountDetail()
    expect(wrapper.findAll('h3').map(title => title.text())).toEqual(['현재 첫 시험', '현재 둘째 시험'])
    await wrapper.get('select').setValue('2')
    expect(wrapper.findAll('h3').map(title => title.text())).toEqual(['종강반 시험'])
  })

  it('aligns mobile zero and full scores with their axis ticks and keeps dates inside the viewBox', async () => {
    window.innerWidth = 390
    vi.mocked(submissionAPI.getStudentSubmissions).mockResolvedValue({ data: [
      { ...records[0], totalScore: 0 }, { ...records[1], totalScore: 100 },
    ] } as never)
    const wrapper = await mountDetail()
    const svg = wrapper.get('svg[viewBox="0 0 350 240"]')
    const scorePoints = svg.findAll('circle').filter(point => point.find('title').exists())
    expect(scorePoints[0]!.attributes('cy')).toBe(svg.get('line[stroke="#303133"]').attributes('y1'))
    expect(scorePoints[1]!.attributes('cy')).toBe(svg.findAll('g line')[0]!.attributes('y1'))
    expect(svg.findAll('text').every(text => Number(text.attributes('y')) < 240)).toBe(true)
  })
})
