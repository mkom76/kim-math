import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/stores/auth'
import StudentClassSwitcher from './StudentClassSwitcher.vue'

const selectStub = { template: '<div class="select-stub"><slot /></div>' }
const optionStub = { template: '<span class="option-stub" />' }

function mountSwitcher(props: { variant?: 'bar' | 'home'; reloadOnSwitch?: boolean } = {}) {
  const pinia = createPinia()
  const authStore = useAuthStore(pinia)
  const wrapper = mount(StudentClassSwitcher, {
    props,
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
    ]
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
    authStore.studentClasses = [
      {
        classId: 9,
        className: '중3 종강반',
        academyId: 1,
        academyName: '테스트 학원',
        status: 'COMPLETED',
        selectable: true,
        readOnly: true,
      },
    ]
    authStore.activeStudentClassId = 9
    authStore.studentClassReadOnly = true
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('종강 · 조회 전용')
  })

  it('keeps class choices collapsed until the student asks to change classes', async () => {
    const { wrapper, authStore } = mountSwitcher({ variant: 'home', reloadOnSwitch: false })
    authStore.studentClasses = [
      {
        classId: 11,
        className: '고1 수학 기본반',
        academyId: 1,
        academyName: '테스트 학원',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
      },
      {
        classId: 12,
        className: '고1 수학 심화반',
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

    expect(wrapper.text()).toContain('학습 반')
    expect(wrapper.text()).toContain('변경')
    expect(wrapper.findAll('.student-class-switcher__choice')).toHaveLength(0)

    await wrapper.get('.student-class-switcher__summary').trigger('click')
    const choices = wrapper.findAll('.student-class-switcher__choice')
    expect(choices).toHaveLength(2)
    expect(choices[0]?.attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).not.toContain('중3 종강반')
  })

  it('switches from the home card without reloading the page', async () => {
    const { wrapper, authStore } = mountSwitcher({ variant: 'home', reloadOnSwitch: false })
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
    ]
    authStore.activeStudentClassId = 11
    const switchClass = vi
      .spyOn(authStore, 'switchStudentClass')
      .mockImplementation(async (classId) => {
        authStore.activeStudentClassId = classId
      })
    await wrapper.vm.$nextTick()

    await wrapper.get('.student-class-switcher__summary').trigger('click')
    await wrapper.findAll('.student-class-switcher__choice')[1]?.trigger('click')
    await flushPromises()

    expect(switchClass).toHaveBeenCalledWith(12)
    expect(wrapper.emitted('switched')).toEqual([[12]])
    expect(wrapper.findAll('.student-class-switcher__choice')).toHaveLength(0)
    expect(wrapper.get('.student-class-switcher__summary').text()).toContain('고1 심화반')
  })
})
