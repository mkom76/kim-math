import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import StudentUiGuideDialog from './StudentUiGuideDialog.vue'

const dialogStub = {
  props: ['modelValue'],
  template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
}
function mountGuide(props = { autoOpen: true }) {
  return mount(StudentUiGuideDialog, { props, global: { stubs: { ElDialog: dialogStub } } })
}
describe('student screen guide', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    useAuthStore().userId = 101
  })
  it('explains the new menus once and remembers acknowledgment across remounts', async () => {
    const wrapper = mountGuide()
    expect(wrapper.text()).toContain('클리닉 신청')
    expect(wrapper.text()).toContain('설정')
    await wrapper.get('button').trigger('click')
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
    const again = mountGuide()
    expect(again.find('button').exists()).toBe(false)
    again.unmount()
  })
  it('shows the guide for another student and when auth loads after the component', async () => {
    window.localStorage.setItem('kim-math:student-guide:2026-11:101', 'seen')
    useAuthStore().userId = null
    const wrapper = mountGuide()
    expect(wrapper.find('button').exists()).toBe(false)
    useAuthStore().userId = 102
    await flushPromises()
    expect(wrapper.find('button').exists()).toBe(true)
    wrapper.unmount()
  })
  it('can be opened from settings even after acknowledgment', async () => {
    window.localStorage.setItem('kim-math:student-guide:2026-11:101', 'seen')
    const wrapper = mountGuide({ autoOpen: false })
    expect(wrapper.find('button').exists()).toBe(false)
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.find('button').exists()).toBe(true)
    wrapper.unmount()
  })
  it('remains dismissible when browser storage is unavailable', async () => {
    const read = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    try {
      const wrapper = mountGuide()
      await wrapper.get('button').trigger('click')
      expect(wrapper.find('button').exists()).toBe(false)
      wrapper.unmount()
    } finally {
      read.mockRestore()
      write.mockRestore()
    }
  })
})
