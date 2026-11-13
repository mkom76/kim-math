import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElCheckbox, ElSelect } from 'element-plus'
import Dialog from './ExamPdfImportDialog.vue'
import { examPdfAPI, type PdfImport } from '@/api/examPdf'
import { homeworkQuestionTypes, homeworkTypeCatalogVersion } from '@/utils/homeworkQuestionTypes'

vi.mock('@/api/examPdf', () => ({ examPdfAPI: { config: vi.fn(), upload: vi.fn(), get: vi.fn(), document: vi.fn(), register: vi.fn(), cancel: vi.fn() } }))
const type = homeworkQuestionTypes.find(item => item.path[0] === '대수')!
const configuration = { enabled: true, ready: true, mock: false, maxBytes: 8388608, maxPages: 20, maxQuestions: 60, catalogVersion: homeworkTypeCatalogVersion, notice: 'PDF 분석 전송 안내' }
const result: PdfImport = { id: 'test-job', state: 'REVIEW', version: 1, testId: null, classId: 10, subjectId: 'ALG', fileName: '숙제.pdf', pageCount: 2, questionPageCount: 1, catalogVersion: homeworkTypeCatalogVersion, reviewRequired: false, expiresAt: '', message: '확인 후 등록', questions: [{ id: 'question-id', position: 1, sourceNumber: '101', text: '가상 문항 내용', regions: [{ page: 1, x: 0, y: 0, width: 1, height: 1 }], suggestedTypeId: type.id, confirmedTypeId: null, typePath: [...type.path], reason: '가상 근거', needsReview: false, answer: "2", points: 5, format: "SUBJECTIVE", multipleAnswers: false }] }
const wrappers: VueWrapper[] = []
const classes = [{ id: 10, name: '가상 반', academyId: 1 }]
function button(wrapper: VueWrapper, text: string) { return wrapper.findAll('button').find(item => item.text().includes(text))! }
async function open() {
  const wrapper = mount(Dialog, { props: { visible: true, classes, academyId: 1 }, global: { plugins: [ElementPlus], stubs: { HomeworkQuestionTypePicker: true, 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' } } } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function chooseAndAnalyze(wrapper: VueWrapper) {
  const input = wrapper.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['%PDF-1.7'], '숙제.pdf', { type: 'application/pdf' })] })
  await input.trigger('change')
  await wrapper.get('input[aria-label="문제 페이지 범위"]').setValue('1')
  await wrapper.get('input[aria-label="해설 페이지 범위"]').setValue('2')
  wrapper.findAllComponents(ElSelect)[0]!.vm.$emit('update:modelValue', 10)
  wrapper.findComponent(ElCheckbox).vm.$emit('update:modelValue', true)
  await flushPromises()
  await button(wrapper, '유형 자동 분류').trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:test-pdf'), revokeObjectURL: vi.fn() })
  vi.mocked(examPdfAPI.config).mockResolvedValue({ data: configuration } as never)
  vi.mocked(examPdfAPI.upload).mockResolvedValue({ data: structuredClone(result) } as never)
  vi.mocked(examPdfAPI.document).mockResolvedValue({ data: new Blob(['%PDF-test'], { type: 'application/pdf' }) } as never)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })

describe('PDF 시험 등록 검토', () => {
  // Element Plus widgets need extra rendering time when the full UI suite runs in parallel.
  it('교사 확인 전에는 등록을 막고 원본 번호와 유형 ID를 그대로 제출한다', async () => {
    const wrapper = await open(); await chooseAndAnalyze(wrapper)
    expect(button(wrapper, '문항 시험 등록').attributes('disabled')).toBeDefined()
    wrapper.findAllComponents(ElCheckbox).at(-1)!.vm.$emit('update:modelValue', true)
    await flushPromises()
    vi.mocked(examPdfAPI.register).mockResolvedValue({ data: { ...result, state: 'REGISTERED', testId: 200 } } as never)
    await button(wrapper, '문항 시험 등록').trigger('click'); await flushPromises()
    expect(examPdfAPI.register).toHaveBeenCalledWith('test-job', expect.objectContaining({ expectedVersion: 1, contentsReviewed: true, questions: [expect.objectContaining({ id: 'question-id', sourceNumber: '101', typeId: type.id, answer: '2', points: 5, format: 'SUBJECTIVE' })] }))
    expect(wrapper.emitted('saved')).toHaveLength(1)
  }, 15000)
  it('다른 학원으로 전환한 뒤 도착한 예전 분석 결과를 표시하지 않는다', async () => {
    let resolve!: (value: unknown) => void
    const pending = new Promise(resolvePending => { resolve = resolvePending })
    vi.mocked(examPdfAPI.upload).mockResolvedValue({ data: { ...result, state: 'PROCESSING', questions: [] } } as never)
    vi.mocked(examPdfAPI.get).mockReturnValue(pending as never)
    const wrapper = await open(); await chooseAndAnalyze(wrapper)
    await wrapper.setProps({ academyId: 2, classes: [{ id: 20, name: '다른 가상 반', academyId: 2 }] }); await flushPromises()
    resolve({ data: { ...result, questions: [{ ...result.questions[0]!, text: '이전 학원 문항' }] } }); await flushPromises()
    expect(wrapper.text()).not.toContain('이전 학원 문항')
    expect(wrapper.findAll('article')).toHaveLength(0)
  }, 15000)
  it('전송 응답을 못 받은 경우 같은 요청 키로 재시도해 중복 분석을 막는다', async () => {
    vi.mocked(examPdfAPI.upload).mockRejectedValueOnce(new Error('network'))
    const wrapper = await open(); await chooseAndAnalyze(wrapper)
    await button(wrapper, '유형 자동 분류').trigger('click'); await flushPromises()
    const calls = vi.mocked(examPdfAPI.upload).mock.calls
    expect(calls).toHaveLength(2)
    expect(calls[0]![3]).toBe(calls[1]![3])
  }, 15000)
  it('분류 실패를 다른 유형으로 채우지 않고 미분류로 보존한다', async () => {
    vi.mocked(examPdfAPI.upload).mockResolvedValue({ data: { ...result, questions: [{ ...result.questions[0]!, suggestedTypeId: null }] } } as never)
    const wrapper = await open(); await chooseAndAnalyze(wrapper)
    expect(wrapper.text()).toContain('미분류 1문항')
    wrapper.findAllComponents(ElCheckbox).at(-1)!.vm.$emit('update:modelValue', true)
    await flushPromises()
    vi.mocked(examPdfAPI.register).mockResolvedValue({ data: { ...result, state: 'REGISTERED', testId: 201 } } as never)
    await button(wrapper, '문항 시험 등록').trigger('click'); await flushPromises()
    expect(vi.mocked(examPdfAPI.register).mock.calls[0]![1].questions[0]!.typeId).toBeNull()
  }, 15000)

  it('해설에 정답·배점이 없으면 등록을 막고 교사의 입력 후 저장한다', async () => {
    vi.mocked(examPdfAPI.upload).mockResolvedValue({ data: { ...result, questions: [{ ...result.questions[0]!, answer: null, points: null }] } } as never)
    const wrapper = await open(); await chooseAndAnalyze(wrapper)
    wrapper.findAllComponents(ElCheckbox).at(-1)!.vm.$emit('update:modelValue', true)
    await flushPromises()
    await button(wrapper, '문항 시험 등록').trigger('click'); await flushPromises()
    expect(examPdfAPI.register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('정답·문항 형식·배점을 확인')
    await wrapper.get('input[placeholder="해설에 없으면 직접 입력"]').setValue('7')
    const points = wrapper.findAllComponents({ name: 'ElInputNumber' }).at(-1)!
    points.vm.$emit('update:modelValue', 4)
    await flushPromises()
    wrapper.findAllComponents(ElCheckbox).at(-1)!.vm.$emit('update:modelValue', true)
    await flushPromises()
    vi.mocked(examPdfAPI.register).mockResolvedValue({ data: { ...result, state: 'REGISTERED', testId: 200 } } as never)
    await button(wrapper, '문항 시험 등록').trigger('click'); await flushPromises()
    expect(examPdfAPI.register).toHaveBeenCalledWith('test-job', expect.objectContaining({ questions: [expect.objectContaining({ answer: '7', points: 4 })] }))
  }, 15000)
})
