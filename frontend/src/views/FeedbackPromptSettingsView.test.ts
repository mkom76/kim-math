import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authAPI, feedbackPromptTemplateAPI } from '../api/client'
import FeedbackPromptSettingsView from './FeedbackPromptSettingsView.vue'

vi.mock('../api/client', () => ({
  authAPI: { getCurrentUser: vi.fn() },
  feedbackPromptTemplateAPI: { getByTeacher: vi.fn(), getDefault: vi.fn(), save: vi.fn() },
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))

const savedTemplate = {
  teacherId: 1, systemPrompt: '작성 가이드', fewShotCount: 2, scoreHistoryCount: 7, isActive: false,
}
const slotStub = { template: '<div><slot name="header"/><slot/></div>' }

async function mountSettings() {
  const wrapper = mount(FeedbackPromptSettingsView, {
    global: {
      stubs: {
        ElCard: slotStub,
        ElForm: slotStub,
        ElFormItem: { props: ['label'], template: '<label>{{ label }}<slot/></label>' },
        ElInput: true,
        ElSwitch: true,
        ElInputNumber: {
          name: 'ElInputNumber', props: ['modelValue', 'min', 'max', 'step'],
          emits: ['update:modelValue'], template: '<input :value="modelValue"/>',
        },
        ElButton: { template: '<button><slot/></button>' },
      },
      directives: { loading: () => {} },
    },
  })
  await flushPromises()
  return wrapper
}

describe('FeedbackPromptSettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authAPI.getCurrentUser).mockResolvedValue({ data: { userId: 1 } } as never)
    vi.mocked(feedbackPromptTemplateAPI.getByTeacher).mockResolvedValue({ ...savedTemplate })
    vi.mocked(feedbackPromptTemplateAPI.save).mockImplementation(async (_id, template) => ({ ...template }))
    vi.mocked(feedbackPromptTemplateAPI.getDefault).mockResolvedValue({
      ...savedTemplate, systemPrompt: '기본 가이드', fewShotCount: 3, scoreHistoryCount: 3,
    })
  })

  it('loads an independent history count bounded from zero to ten even with the default prompt', async () => {
    const wrapper = await mountSettings()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs).toHaveLength(2)
    expect(inputs[0]!.props('modelValue')).toBe(2)
    expect(inputs[1]!.props()).toMatchObject({ modelValue: 7, min: 0, max: 10, step: 1 })
    expect(wrapper.text()).toContain('이전 시험 반영 개수')
    expect(wrapper.text()).toContain('이번 시험은 개수에 포함하지 않으며')
  })

  it('saves zero without altering the separate few-shot count or enabling the custom prompt', async () => {
    const wrapper = await mountSettings()
    wrapper.findAllComponents({ name: 'ElInputNumber' })[1]!.vm.$emit('update:modelValue', 0)
    await wrapper.findAll('button').find(button => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(feedbackPromptTemplateAPI.save).toHaveBeenCalledWith(1, expect.objectContaining({
      fewShotCount: 2, scoreHistoryCount: 0, isActive: false,
    }))
  })

  it('resets both counts to three and persists the reset on save', async () => {
    const wrapper = await mountSettings()
    await wrapper.findAll('button').find(button => button.text() === '기본값으로 초기화')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(feedbackPromptTemplateAPI.save).toHaveBeenCalledWith(1, expect.objectContaining({
      systemPrompt: '기본 가이드', fewShotCount: 3, scoreHistoryCount: 3,
    }))
  })
})
