import { computed, inject, provide } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import { testAPI } from '@/api/client'
import TestAnswersView from './TestAnswersView.vue'

vi.mock('@/api/client', () => ({ testAPI: { getTestQuestions: vi.fn(), saveTestAnswers: vi.fn() } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '10' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

const slotStub = { template: '<div><slot /></div>' }
const initialQuestions = [
  { id: 11, number: 1, answer: '1', points: 30, questionType: 'OBJECTIVE' },
  { id: 22, number: 2, answer: '2', points: 30, questionType: 'OBJECTIVE' },
  { id: 33, number: 3, answer: '3', points: 40, questionType: 'OBJECTIVE' },
]

async function mountAnswers() {
  const wrapper = shallowMount(TestAnswersView, {
    global: {
      stubs: {
        ...Object.fromEntries([
          'Plus', 'Collection', 'ArrowLeft', 'List', 'Select', 'Edit', 'Memo', 'Check', 'Delete',
          'ElTag', 'ElOption', 'ElSelect', 'ElSwitch', 'ElCheckboxButton', 'ElCheckboxGroup',
          'ElRadioButton', 'ElRadioGroup', 'ElInput', 'ElInputNumber',
        ].map(name => [name, true])),
        ElCard: slotStub,
        ElIcon: true, ElEmpty: true, ElAlert: true, EditPen: true, Document: true,
        ElButton: {
          props: { circle: Boolean, disabled: Boolean },
          template: '<button :data-delete="!!circle" :disabled="disabled"><slot /></button>',
        },
        ElTable: {
          props: ['data'],
          setup(props) { provide('questionRows', computed(() => props.data)) },
          template: '<div><slot /></div>',
        },
        ElTableColumn: {
          props: ['label'],
          setup: () => ({ rows: inject('questionRows') }),
          template: '<div v-if="label === \'작업\'"><slot v-for="(row, index) in rows" :row="row" :$index="index" /></div>',
        },
      },
      directives: { loading: () => {} },
    },
  })
  await flushPromises()
  return wrapper
}

describe('TestAnswersView question identity', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    vi.mocked(testAPI.getTestQuestions)
      .mockResolvedValueOnce({ data: structuredClone(initialQuestions) } as never)
      .mockResolvedValue({ data: [] } as never)
    vi.mocked(testAPI.saveTestAnswers).mockResolvedValue({} as never)
  })

  it.each([0, 1])('keeps existing IDs when deleting question at index %s and renumbering', async deletedIndex => {
    const wrapper = await mountAnswers()
    await wrapper.findAll('button[data-delete="true"]')[deletedIndex]!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '전체 저장')!.trigger('click')
    await flushPromises()

    const answers = vi.mocked(testAPI.saveTestAnswers).mock.calls[0]![1]
    expect(vi.mocked(testAPI.saveTestAnswers).mock.calls[0]![2]).toEqual([11, 22, 33])
    expect(answers.map(({ id, number, answer }) => ({ id, number, answer }))).toEqual(
      deletedIndex === 0
        ? [{ id: 22, number: 1, answer: '2' }, { id: 33, number: 2, answer: '3' }]
        : [{ id: 11, number: 1, answer: '1' }, { id: 33, number: 2, answer: '3' }],
    )
    wrapper.unmount()
  })

  it('sends null IDs only for newly added questions', async () => {
    const wrapper = await mountAnswers()
    await wrapper.findAll('button').find(button => button.text() === '문제 추가')!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '전체 저장')!.trigger('click')
    await flushPromises()

    const answers = vi.mocked(testAPI.saveTestAnswers).mock.calls[0]![1]
    expect(vi.mocked(testAPI.saveTestAnswers).mock.calls[0]![2]).toEqual([11, 22, 33])
    expect(answers.map(({ id, number }) => ({ id, number }))).toEqual([
      { id: 11, number: 1 }, { id: 22, number: 2 }, { id: 33, number: 3 }, { id: null, number: 4 },
    ])
    wrapper.unmount()
  })

  it('keeps the original snapshot after a failed post-save refresh and shows a stale-save error', async () => {
    vi.mocked(testAPI.getTestQuestions).mockRejectedValueOnce(new Error('새로고침 실패'))
    const wrapper = await mountAnswers()
    const add = () => wrapper.findAll('button').find(button => button.text() === '문제 추가')!
    const save = () => wrapper.findAll('button').find(button => button.text() === '전체 저장')!
    await add().trigger('click')
    await save().trigger('click')
    await flushPromises()

    vi.mocked(testAPI.saveTestAnswers).mockRejectedValueOnce({
      response: { status: 409, data: { message: '시험 문항이 변경되었습니다. 새로고침 후 다시 시도해주세요' } },
    })
    await add().trigger('click')
    await save().trigger('click')
    await flushPromises()
    expect(vi.mocked(testAPI.saveTestAnswers).mock.calls.map(call => call[2])).toEqual([
      [11, 22, 33], [11, 22, 33],
    ])
    expect(ElMessage.error).toHaveBeenLastCalledWith('시험 문항이 변경되었습니다. 새로고침 후 다시 시도해주세요')
    wrapper.unmount()
  })
})
