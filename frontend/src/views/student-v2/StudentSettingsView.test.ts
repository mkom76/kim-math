import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import StudentSettingsView from './StudentSettingsView.vue'

const { logout, push, replace, errorMessage } = vi.hoisted(() => ({
  logout: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  errorMessage: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ logout }),
}))

vi.mock('@/composables/usePinChange', () => ({
  usePinChange: () => ({
    loading: false,
    pinLength: 4,
    pinForm: { currentPin: '', newPin: '', confirmPin: '' },
    handleChangePIN: vi.fn(),
  }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: errorMessage },
}))

const logoutDialogStub = {
  props: ['modelValue', 'loading'],
  emits: ['update:modelValue', 'confirm'],
  template: `
    <section v-if="modelValue" data-test="logout-dialog">
      <button data-test="cancel-logout" @click="$emit('update:modelValue', false)">계속 이용하기</button>
      <button data-test="confirm-logout" @click="$emit('confirm')">로그아웃</button>
    </section>
  `,
}

function mountView() {
  return mount(StudentSettingsView, {
    global: {
      directives: { loading: () => undefined },
      stubs: {
        StudentLogoutDialog: logoutDialogStub,
        StudentUiFeedbackDialog: true,
        ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
        ElInput: true,
        ElIcon: { template: '<span><slot/></span>' },
      },
    },
  })
}

describe('StudentSettingsView logout', () => {
  beforeEach(() => {
    logout.mockReset()
    logout.mockResolvedValue(undefined)
    push.mockReset()
    replace.mockReset()
    errorMessage.mockReset()
  })

  it('opens the V2 logout dialog without immediately logging out', async () => {
    const wrapper = mountView()

    await wrapper.find('.settings-row--danger').trigger('click')

    expect(wrapper.find('[data-test="logout-dialog"]').exists()).toBe(true)
    expect(logout).not.toHaveBeenCalled()
  })

  it('logs out and moves to login only after confirmation', async () => {
    const wrapper = mountView()

    await wrapper.find('.settings-row--danger').trigger('click')
    await wrapper.find('[data-test="confirm-logout"]').trigger('click')
    await flushPromises()

    expect(logout).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith('/login')
  })

  it('closes the dialog without logging out when the student cancels', async () => {
    const wrapper = mountView()

    await wrapper.find('.settings-row--danger').trigger('click')
    await wrapper.find('[data-test="cancel-logout"]').trigger('click')

    expect(wrapper.find('[data-test="logout-dialog"]').exists()).toBe(false)
    expect(logout).not.toHaveBeenCalled()
  })
})
