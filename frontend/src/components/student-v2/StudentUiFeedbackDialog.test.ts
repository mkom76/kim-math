import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import StudentUiFeedbackDialog from './StudentUiFeedbackDialog.vue'

const { createFeedback, successMessage } = vi.hoisted(() => ({
  createFeedback: vi.fn(),
  successMessage: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  studentUiFeedbackAPI: { create: createFeedback },
}))

vi.mock('@/utils/platform', () => ({
  platformName: () => 'web',
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/student/dashboard' }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: successMessage, error: vi.fn() },
}))

const dialogStub = {
  template: '<section><slot name="header"/><slot/><slot name="footer"/></section>',
}
const buttonStub = {
  inheritAttrs: false,
  emits: ['click'],
  template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\')"><slot/></button>',
}

function mountDialog() {
  return mount(StudentUiFeedbackDialog, {
    props: { modelValue: true },
    global: {
      stubs: {
        ElDialog: dialogStub,
        ElButton: buttonStub,
        ElInput: true,
        ElIcon: { template: '<span><slot/></span>' },
      },
    },
  })
}

function buttonWithText(wrapper: ReturnType<typeof mountDialog>, text: string) {
  const button = wrapper.findAll('button').find(item => item.text().includes(text))
  if (!button) throw new Error(`${text} 버튼을 찾을 수 없습니다`)
  return button
}

describe('StudentUiFeedbackDialog', () => {
  beforeEach(() => {
    createFeedback.mockReset()
    createFeedback.mockResolvedValue({ data: { id: 1, createdAt: '2026-08-16T12:00:00' } })
    successMessage.mockReset()
  })

  it('submits a short positive response with automatic context', async () => {
    const wrapper = mountDialog()

    await buttonWithText(wrapper, '좋아요').trigger('click')
    await buttonWithText(wrapper, '의견 보내기').trigger('click')
    await flushPromises()

    expect(createFeedback).toHaveBeenCalledWith(expect.objectContaining({
      sentiment: 'POSITIVE',
      pagePath: '/student/dashboard',
      uiVersion: 'v2',
      platform: 'web',
      appVersion: '1.0.3',
    }))
    expect(successMessage).toHaveBeenCalledWith('의견을 보내주셔서 고마워요!')
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })

  it('requires a category for improvement feedback', async () => {
    const wrapper = mountDialog()

    await buttonWithText(wrapper, '불편해요').trigger('click')
    expect(buttonWithText(wrapper, '의견 보내기').attributes('disabled')).toBeDefined()

    await buttonWithText(wrapper, '메뉴·이동').trigger('click')
    expect(buttonWithText(wrapper, '의견 보내기').attributes('disabled')).toBeUndefined()
  })
})
