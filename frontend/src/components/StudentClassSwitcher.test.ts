import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { useAuthStore } from '@/stores/auth'
import StudentClassSwitcher from './StudentClassSwitcher.vue'

const selectStub = { template: '<div class="select-stub"><slot /></div>' }
const optionStub = { template: '<span class="option-stub" />' }

function mountSwitcher() {
  const pinia = createPinia()
  const authStore = useAuthStore(pinia)
  const wrapper = mount(StudentClassSwitcher, {
    global: {
      plugins: [pinia],
      stubs: {
        ElSelect: selectStub,
        ElOption: optionStub,
      },
    },
  })
  return { wrapper, authStore }
}

describe('StudentClassSwitcher', () => {
  it('shows a plain class label when only one class can be selected', async () => {
    const { wrapper, authStore } = mountSwitcher()
    authStore.studentClasses = [{
      classId: 11,
      className: '고1 A반',
      academyId: 1,
      academyName: '테스트 학원',
      status: 'ACTIVE',
      selectable: true,
      readOnly: false,
    }]
    authStore.activeStudentClassId = 11
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('현재 반')
    expect(wrapper.text()).toContain('고1 A반')
    expect(wrapper.find('.select-stub').exists()).toBe(false)
  })

  it('shows a dropdown for multiple active classes and excludes completed history', async () => {
    const { wrapper, authStore } = mountSwitcher()
    authStore.studentClasses = [
      {
        classId: 11,
        className: '고1 A반',
        academyId: 1,
        academyName: '테스트 학원',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
      },
      {
        classId: 12,
        className: '고1 심화반',
        academyId: 1,
        academyName: '테스트 학원',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
      },
      {
        classId: 9,
        className: '중3 종강반',
        academyId: 1,
        academyName: '테스트 학원',
        status: 'COMPLETED',
        selectable: false,
        readOnly: true,
      },
    ]
    authStore.activeStudentClassId = 11
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.select-stub').exists()).toBe(true)
    expect(wrapper.findAll('.option-stub')).toHaveLength(2)
  })

  it('marks the completed fallback as read-only', async () => {
    const { wrapper, authStore } = mountSwitcher()
    authStore.studentClasses = [{
      classId: 9,
      className: '중3 종강반',
      academyId: 1,
      academyName: '테스트 학원',
      status: 'COMPLETED',
      selectable: true,
      readOnly: true,
    }]
    authStore.activeStudentClassId = 9
    authStore.studentClassReadOnly = true
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('종강 · 조회 전용')
  })
})
