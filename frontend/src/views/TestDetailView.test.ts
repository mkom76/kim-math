import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submissionAPI, testAPI } from '@/api/client'
import TestDetailView from './TestDetailView.vue'

vi.mock('@/api/client', () => ({
  testAPI: { getTest: vi.fn(), getTestStats: vi.fn(), getSubmissionRoster: vi.fn(), getTestQuestions: vi.fn() },
  submissionAPI: { getSubmissionWithDetails: vi.fn(), saveAnswersForStudent: vi.fn(), gradeEssay: vi.fn() },
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '10' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), info: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

const rows = [
  { studentId: 1, studentName: '학생 A', submitted: true, submissionId: 11 },
  { studentId: 2, studentName: '학생 B', submitted: true, submissionId: 22 },
]
const writeText = vi.fn()
const slotStub = { template: '<div><slot /></div>' }

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(complete => { resolve = complete })
  return { promise, resolve }
}

async function mountDetail() {
  const wrapper = shallowMount(TestDetailView, {
    global: {
      stubs: {
        ...Object.fromEntries([
          'ElIcon', 'ElAvatar', 'ElDescriptions', 'ElDescriptionsItem', 'ElTag', 'ElProgress',
          'ElEmpty', 'ElDialog', 'Document', 'InfoFilled', 'DataAnalysis', 'TrendCharts',
          'TrophyBase', 'User', 'Calendar',
        ].map(name => [name, true])),
        ElCard: slotStub, ElRow: slotStub, ElCol: slotStub, ElTable: slotStub,
        ElTableColumn: {
          props: ['label'], setup: () => ({ rows }),
          template: '<div v-if="label === \'관리\' || label === \'서술형 점수\'"><slot v-for="row in rows" :row="row" /></div>',
        },
        ElButton: {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading" :data-loading="!!loading"><slot /></button>',
        },
        ElDialog: {
          props: ['modelValue'],
          template: '<section v-if="modelValue" role="dialog"><slot /><slot name="footer" /></section>',
        },
        ElInput: {
          props: ['modelValue'], emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
      },
      directives: { loading: () => {} },
    },
  })
  await flushPromises()
  const buttons = wrapper.findAll('button').filter(button => button.text() === '오답 복사')
  expect(buttons).toHaveLength(2)
  return { wrapper, buttons }
}

describe('TestDetailView incorrect answer copying', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    writeText.mockResolvedValue(undefined)
    vi.mocked(testAPI.getTest).mockResolvedValue({ data: { title: '시험' } } as never)
    vi.mocked(testAPI.getTestStats).mockResolvedValue({ data: {} } as never)
    vi.mocked(testAPI.getSubmissionRoster).mockResolvedValue({ data: rows } as never)
    vi.mocked(testAPI.getTestQuestions).mockResolvedValue({ data: [] } as never)
  })

  it('keeps manual essay scores available with empty statistics and loads existing scores', async () => {
    vi.mocked(testAPI.getTestQuestions).mockResolvedValue({ data: [
      { id: 101, number: 1, questionType: 'OBJECTIVE' },
      { id: 102, number: 2, questionType: 'ESSAY' },
    ] } as never)
    vi.mocked(submissionAPI.getSubmissionWithDetails).mockResolvedValue({ data: { details: [
      { id: 81, questionNumber: 1, questionType: 'OBJECTIVE', earnedPoints: 50 },
      { id: 82, questionNumber: 2, questionType: 'ESSAY', earnedPoints: 12, maxPoints: 20, teacherComment: '기존 코멘트' },
    ] } } as never)
    vi.mocked(submissionAPI.gradeEssay).mockResolvedValue({ data: {} } as never)
    const { wrapper } = await mountDetail()
    const button = wrapper.findAll('button').find(button => button.text() === '점수 입력·수정')
    expect(button).toBeDefined()
    await button!.trigger('click'); await flushPromises()
    expect(wrapper.find('[role="dialog"]').text()).toContain('2번 문제')
    expect(wrapper.find('[role="dialog"]').text()).not.toContain('1번 문제')
    const save = wrapper.findAll('button').find(button => button.text() === '채점 완료')
    await save!.trigger('click'); await flushPromises()
    expect(submissionAPI.gradeEssay).toHaveBeenCalledExactlyOnceWith(82, 12, '기존 코멘트')
    expect(submissionAPI.saveAnswersForStudent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('blocks all copy buttons until both the request and clipboard write finish, including immediate repeat clicks', async () => {
    const request = deferred<never>()
    const clipboard = deferred<void>()
    vi.mocked(submissionAPI.getSubmissionWithDetails).mockReturnValueOnce(request.promise)
    writeText.mockReturnValueOnce(clipboard.promise)
    const { wrapper, buttons } = await mountDetail()

    buttons[0]!.element.click()
    buttons[1]!.element.click()
    await flushPromises()
    expect(submissionAPI.getSubmissionWithDetails).toHaveBeenCalledExactlyOnceWith(11)
    expect(buttons.every(button => button.element.disabled)).toBe(true)
    expect(buttons.map(button => button.attributes('data-loading'))).toEqual(['true', 'false'])

    request.resolve({ data: { details: [{ questionNumber: 3, isCorrect: false }] } } as never)
    await flushPromises()
    expect(writeText).toHaveBeenCalledExactlyOnceWith('3')
    expect(buttons.every(button => button.element.disabled)).toBe(true)

    clipboard.resolve()
    await flushPromises()
    expect(buttons.every(button => !button.element.disabled)).toBe(true)
    vi.mocked(submissionAPI.getSubmissionWithDetails).mockResolvedValueOnce({
      data: { details: [{ questionNumber: 7, isCorrect: false }] },
    } as never)
    await buttons[1]!.trigger('click')
    await flushPromises()
    expect(submissionAPI.getSubmissionWithDetails).toHaveBeenLastCalledWith(22)
    expect(writeText).toHaveBeenLastCalledWith('7')
    wrapper.unmount()
  })

  it.each(['request failure', 'no incorrect answers'])('re-enables every copy button after %s', async outcome => {
    if (outcome === 'request failure') {
      vi.mocked(submissionAPI.getSubmissionWithDetails).mockRejectedValueOnce(new Error('조회 실패'))
    } else {
      vi.mocked(submissionAPI.getSubmissionWithDetails).mockResolvedValueOnce({ data: { details: [] } } as never)
    }
    const { wrapper, buttons } = await mountDetail()
    await buttons[0]!.trigger('click')
    await flushPromises()
    expect(buttons.every(button => !button.element.disabled)).toBe(true)
    expect(writeText).not.toHaveBeenCalled()
    expect(outcome === 'request failure' ? ElMessage.error : ElMessage.info).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('sends the loaded question snapshot and preserves the answer dialog when a changed exam rejects saving', async () => {
    const updatedAt = '2026-11-07T20:00:00'
    vi.mocked(testAPI.getTestQuestions).mockResolvedValue({ data: [
      { id: 101, number: 1, points: 100, questionType: 'SUBJECTIVE', updatedAt },
    ] } as never)
    vi.mocked(submissionAPI.getSubmissionWithDetails).mockResolvedValue({
      data: { details: [{ questionNumber: 1, studentAnswer: '3' }] },
    } as never)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    const message = '시험 문항이 변경되었습니다. 새로고침 후 다시 시도해주세요'
    vi.mocked(submissionAPI.saveAnswersForStudent).mockRejectedValue({ response: { status: 409, data: { message } } })
    const { wrapper } = await mountDetail()
    await wrapper.findAll('button').find(button => button.text() === '답안 수정')!.trigger('click')
    await flushPromises()
    await wrapper.get('[role="dialog"] input').setValue('4')
    await wrapper.get('[role="dialog"]').findAll('button').find(button => button.text() === '저장')!.trigger('click')
    await flushPromises()

    expect(submissionAPI.saveAnswersForStudent).toHaveBeenCalledWith(1, 10, { 1: '4' }, [
      { id: 101, number: 1, updatedAt },
    ])
    expect(ElMessage.error).toHaveBeenCalledWith(message)
    expect(wrapper.get('[role="dialog"] input').element).toHaveProperty('value', '4')
    wrapper.unmount()
  })
})
