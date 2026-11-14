import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import { omrAPI, type OmrScan } from '@/api/omr'
import OmrImportDialog from './OmrImportDialog.vue'

vi.mock('@/api/omr', () => ({ omrAPI: { scan: vi.fn(), review: vi.fn(), confirm: vi.fn() } }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn() }, ElMessageBox: { confirm: vi.fn() },
}))

const box = { x: .5, y: .1, width: .2, height: .03 }
const result: OmrScan = {
  expectedQuestions: [{ id: 11, number: 1, updatedAt: '2026-11-14T10:00:00' }, { id: 12, number: 2, updatedAt: null }],
  questions: [{ number: 1, multipleAnswers: false }, { number: 2, multipleAnswers: false }],
  pages: [{ number: 1, recognized: true, message: '', preview: 'data:image/jpeg;base64,test', marks: [
    { number: 1, answer: '', status: 'UNCERTAIN', box }, { number: 2, answer: '1,3', status: 'MULTIPLE', box },
  ] }],
}
const review = {
  revision: 'revision-1', existing: false, previousScore: null, preservedPoints: 0, maxPoints: 100,
  preservedQuestionCount: 0, expectedQuestions: result.expectedQuestions,
  objectives: [
    { number: 1, answer: null, correctAnswer: '4', multipleAnswers: false, points: 50 },
    { number: 2, answer: null, correctAnswer: '1', multipleAnswers: false, points: 50 },
  ],
}
function setup() {
  return mount(OmrImportDialog, { props: { testId: 7, roster: [
    { studentId: 1, studentName: '가상 학생 A', submitted: false },
    { studentId: 2, studentName: '가상 학생 B', submitted: true },
  ] }, global: { directives: { loading: () => {} }, stubs: {
    ElDialog: { template: '<section role="dialog"><slot /><slot name="footer" /></section>' },
    ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    ElAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
  } } })
}
async function upload(wrapper: ReturnType<typeof setup>, file = new File(['%PDF-test'], 'scan.pdf', { type: 'application/pdf' })) {
  const input = wrapper.get('#omr-file')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await wrapper.findAll('button').find(b => b.text() === '답안 인식')!.trigger('click')
  await flushPromises()
}
const saveButton = (wrapper: ReturnType<typeof setup>) => wrapper.findAll('button').find(b => b.text() === '이 학생 저장·채점')!

describe('OMR import review', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(omrAPI.scan).mockResolvedValue({ data: structuredClone(result) } as never)
    vi.mocked(omrAPI.confirm).mockResolvedValue({ data: { totalScore: 50 } } as never)
    vi.mocked(omrAPI.review).mockResolvedValue({ data: structuredClone(review) } as never)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
  })

  it('requires student selection, ambiguous-mark review and final confirmation before creating a submission', async () => {
    const wrapper = setup()
    await upload(wrapper)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.get('#omr-student option[value="2"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('#omr-student').setValue('1')
    await flushPromises()
    await wrapper.get('input[aria-label="1번 답안"]').setValue('4')
    const checks = wrapper.findAll('input[type="checkbox"]')
    expect(checks).toHaveLength(3)
    expect(checks[2]!.attributes('disabled')).toBeDefined()
    await checks[0]!.setValue(true)
    await checks[1]!.setValue(true)
    await checks[2]!.setValue(true)
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(omrAPI.confirm).toHaveBeenCalledExactlyOnceWith(7, 1, { 1: '4', 2: '1,3' }, result.expectedQuestions, 'revision-1')
    expect(wrapper.text()).toContain('저장·채점 완료: 50점')
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('keeps edited answers when a stale exam or existing submission rejects confirmation', async () => {
    vi.mocked(omrAPI.confirm).mockRejectedValue({ response: { data: { message: '시험 문항이 변경되었습니다.' } } })
    const wrapper = setup()
    await upload(wrapper)
    await wrapper.get('#omr-student').setValue('1')
    await flushPromises()
    await wrapper.get('input[aria-label="1번 답안"]').setValue('5')
    for (const check of wrapper.findAll('input[type="checkbox"]')) await check.setValue(true)
    await saveButton(wrapper).trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('시험 문항이 변경되었습니다.')
    expect((wrapper.get('input[aria-label="1번 답안"]').element as HTMLInputElement).value).toBe('5')
    expect(wrapper.emitted('saved')).toBeUndefined()
    wrapper.unmount()
  })

  it('shows existing objective changes and the score that preserves essay points before updating an existing student', async () => {
    vi.mocked(omrAPI.review).mockResolvedValue({ data: { ...review, existing: true, previousScore: 20,
      preservedPoints: 12, maxPoints: 120, preservedQuestionCount: 2,
      objectives: [{ number: 1, answer: '3', correctAnswer: '4', multipleAnswers: false, points: 50 },
        { number: 2, answer: '1', correctAnswer: '1', multipleAnswers: false, points: 50 }] } } as never)
    const wrapper = setup(); await upload(wrapper)
    await wrapper.get('#omr-student').setValue('2'); await flushPromises()
    await wrapper.get('input[aria-label="1번 답안"]').setValue('4')
    for (const check of wrapper.findAll('input[type="checkbox"]')) await check.setValue(true)
    expect(wrapper.text()).toContain('기존 20점 → 예상 52점')
    expect(wrapper.text()).toContain('획득 12점 보존')
    expect(wrapper.text()).toContain('기존 3 → 4')
    await saveButton(wrapper).trigger('click'); await flushPromises()
    expect(omrAPI.confirm).toHaveBeenCalledWith(7, 2, { 1: '4', 2: '1,3' }, result.expectedQuestions, 'revision-1')
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(expect.stringContaining('주관식·서술형 답안과 점수는 보존'), 'OMR 채점 확정', expect.any(Object))
    wrapper.unmount()
  })

  it('explains an empty server error without claiming that answers were saved', async () => {
    vi.mocked(omrAPI.confirm).mockRejectedValue({ response: { status: 500, data: { message: 'No message available' } } })
    const wrapper = setup(); await upload(wrapper)
    await wrapper.get('#omr-student').setValue('1'); await flushPromises()
    for (const check of wrapper.findAll('input[type="checkbox"]')) await check.setValue(true)
    await saveButton(wrapper).trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('저장 결과를 확인하지 못했습니다.')
    expect(wrapper.text()).toContain('오류 코드: 500')
    expect(wrapper.text()).not.toContain('No message available')
    expect(wrapper.emitted('saved')).toBeUndefined()
    wrapper.unmount()
  })

  it('blocks saving when the review cannot be loaded or question snapshots changed', async () => {
    vi.mocked(omrAPI.review).mockRejectedValueOnce(new Error('offline'))
    const wrapper = setup(); await upload(wrapper)
    await wrapper.get('#omr-student').setValue('2'); await flushPromises()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    vi.mocked(omrAPI.review).mockResolvedValue({ data: { ...review, expectedQuestions: [] } } as never)
    await wrapper.findAll('button').find(b => b.text() === '기존 답안 다시 확인')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('시험 문항이 변경되었습니다.')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('ignores an old review response after the selected student changes', async () => {
    let resolveOld!: (value: never) => void
    vi.mocked(omrAPI.review).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }) as never)
    const wrapper = setup(); await upload(wrapper)
    await wrapper.get('#omr-student').setValue('1')
    await wrapper.get('#omr-student').setValue('2'); await flushPromises()
    resolveOld({ data: { ...review, existing: true, previousScore: 99 } } as never)
    await flushPromises()
    expect(wrapper.text()).not.toContain('기존 99점')
    expect(wrapper.text()).toContain('신규 제출 · 예상')
    wrapper.unmount()
  })

  it('never offers to confirm an unrecognized page and confirms discarding unfinished work', async () => {
    const failed = structuredClone(result)
    failed.pages[0]!.recognized = false; failed.pages[0]!.marks = []; failed.pages[0]!.message = '양식 인식 실패'
    vi.mocked(omrAPI.scan).mockResolvedValue({ data: failed } as never)
    const wrapper = setup(); await upload(wrapper)
    expect(wrapper.text()).toContain('양식 인식 실패')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find(b => b.text() === '닫기')!.trigger('click'); await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(omrAPI.confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('prevents assigning one student to multiple scanned pages', async () => {
    const twoPages = structuredClone(result)
    twoPages.pages.push({ ...structuredClone(twoPages.pages[0]!), number: 2 })
    vi.mocked(omrAPI.scan).mockResolvedValue({ data: twoPages } as never)
    const wrapper = setup(); await upload(wrapper)
    await wrapper.get('#omr-student').setValue('1')
    await wrapper.get('#omr-page').setValue('1')
    expect(wrapper.get('#omr-student option[value="1"]').attributes('disabled')).toBeDefined()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('rejects non-PDF files before upload', async () => {
    const wrapper = setup()
    await upload(wrapper, new File(['photo'], 'photo.jpg'))
    expect(wrapper.text()).toContain('8MB 이하의 스캔 PDF')
    expect(omrAPI.scan).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
