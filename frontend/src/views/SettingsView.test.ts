import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth'
import SettingsView from './SettingsView.vue'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

vi.mock('@/api/client', () => ({
  authAPI: {
    changePin: vi.fn(),
  },
}))

const slotStub = { template: '<div><slot name="header"/><slot/></div>' }

function mountAs(role: 'STUDENT' | 'TEACHER') {
  const pinia = createPinia()
  const authStore = useAuthStore(pinia)
  authStore.role = role

  return mount(SettingsView, {
    global: {
      plugins: [pinia],
      stubs: {
        ElCard: slotStub,
        ElIcon: slotStub,
        ElForm: slotStub,
        ElFormItem: slotStub,
        ElInput: true,
        ElButton: slotStub,
        ElAlert: slotStub,
      },
    },
  })
}

describe('SettingsView', () => {
  beforeEach(() => push.mockClear())

  it('hides AI feedback prompt settings from students', () => {
    const wrapper = mountAs('STUDENT')

    expect(wrapper.find('[data-test="feedback-prompt-settings"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('AI 피드백 프롬프트 설정')
  })

  it('shows AI feedback prompt settings to teachers', () => {
    const wrapper = mountAs('TEACHER')

    expect(wrapper.find('[data-test="feedback-prompt-settings"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI 피드백 프롬프트 설정')
  })
})
