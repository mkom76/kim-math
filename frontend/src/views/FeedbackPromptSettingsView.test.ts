import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
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
        ElFormItem: { props: ['label'], template: '<div><slot name="label">{{ label }}</slot><slot/></div>' },
        ElInput: {
          name: 'ElInput', props: { modelValue: String, readonly: Boolean }, emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" :readonly="readonly"/>',
        },
        ElDialog: {
          props: ['modelValue', 'title'],
          template: '<section v-if="modelValue" role="dialog"><h1>{{ title }}</h1><slot/><slot name="footer"/></section>',
        },
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

  it.each([true, false])('previews the default prompt read-only and preserves unsaved edits with custom mode %s', async isActive => {
    vi.mocked(feedbackPromptTemplateAPI.getByTeacher).mockResolvedValue({ ...savedTemplate, isActive })
    const defaultPrompt = '기본 가이드\n\n## 성적 추이 해석 원칙\n점수 증감과 반 평균 대비 격차 변화를 함께 참고합니다.'
    vi.mocked(feedbackPromptTemplateAPI.getDefault).mockResolvedValue({
      ...savedTemplate, systemPrompt: defaultPrompt, fewShotCount: 3, scoreHistoryCount: 3,
    })
    const wrapper = await mountSettings()
    const editor = wrapper.findComponent({ name: 'ElInput' })
    editor.vm.$emit('update:modelValue', '저장 전 커스텀 내용')
    const counts = wrapper.findAllComponents({ name: 'ElInputNumber' })
    counts[0]!.vm.$emit('update:modelValue', 4)
    counts[1]!.vm.$emit('update:modelValue', 8)

    await wrapper.findAll('button').find(button => button.text() === '기본 프롬프트 보기')!.trigger('click')
    await flushPromises()

    expect(feedbackPromptTemplateAPI.getDefault).toHaveBeenCalledOnce()
    const preview = wrapper.get('[role="dialog"]').findComponent({ name: 'ElInput' })
    expect(preview.props()).toMatchObject({ modelValue: defaultPrompt, readonly: true })
    expect(preview.get('textarea').element).toHaveProperty('readOnly', true)
    expect(editor.props('modelValue')).toBe('저장 전 커스텀 내용')
    expect(counts[0]!.props('modelValue')).toBe(4)
    expect(counts[1]!.props('modelValue')).toBe(8)
    expect(feedbackPromptTemplateAPI.save).not.toHaveBeenCalled()

    await wrapper.findAll('button').find(button => button.text() === '닫기')!.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(feedbackPromptTemplateAPI.save).toHaveBeenCalledWith(1, expect.objectContaining({
      systemPrompt: '저장 전 커스텀 내용', fewShotCount: 4, scoreHistoryCount: 8, isActive,
    }))
  })

  it('preserves the custom draft when loading the default prompt fails', async () => {
    vi.mocked(feedbackPromptTemplateAPI.getDefault).mockRejectedValue(new Error('조회 실패'))
    const wrapper = await mountSettings()
    await wrapper.findAll('button').find(button => button.text() === '기본 프롬프트 보기')!.trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('기본 프롬프트를 불러오는데 실패했습니다')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ElInput' }).props('modelValue')).toBe(savedTemplate.systemPrompt)
    expect(feedbackPromptTemplateAPI.save).not.toHaveBeenCalled()
  })
})
