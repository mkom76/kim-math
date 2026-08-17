import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import StudentNotificationsView from './StudentNotificationsView.vue'

const {
  getInbox,
  markRead,
  markAllRead,
  push,
  resolve,
  warningMessage,
} = vi.hoisted(() => ({
  getInbox: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
  push: vi.fn(),
  resolve: vi.fn((path: string) => ({ matched: path === '/student/unknown' ? [] : [{}] })),
  warningMessage: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  studentNotificationAPI: {
    getInbox,
    markRead,
    markAllRead,
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, resolve, back: vi.fn() }),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: warningMessage,
  },
}))

const unreadNotification = {
  id: 1,
  type: 'FEEDBACK',
  title: '피드백 도착',
  body: '선생님이 피드백을 남겼어요',
  targetPath: '/student/daily-feedback',
  createdAt: '2026-08-17T12:00:00',
}

function mountView() {
  return mount(StudentNotificationsView, {
    global: {
      directives: { loading: {} },
      stubs: { ElIcon: { template: '<span><slot/></span>' } },
    },
  })
}

describe('StudentNotificationsView', () => {
  beforeEach(() => {
    getInbox.mockReset()
    markRead.mockReset()
    markAllRead.mockReset()
    push.mockReset()
    resolve.mockClear()
    warningMessage.mockReset()
    getInbox.mockResolvedValue({ data: [{ ...unreadNotification }] })
    markRead.mockResolvedValue({
      data: { ...unreadNotification, readAt: '2026-08-17T12:10:00' },
    })
    markAllRead.mockResolvedValue({ data: { updated: 1 } })
    push.mockResolvedValue(undefined)
  })

  it('marks an unread item before opening its target', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.notification-row').trigger('click')
    await flushPromises()

    expect(markRead).toHaveBeenCalledWith(1)
    expect(push).toHaveBeenCalledWith('/student/daily-feedback')
    expect(wrapper.find('.notification-row').classes()).not.toContain('unread')
  })

  it('still opens the target when marking read fails', async () => {
    markRead.mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.notification-row').trigger('click')
    await flushPromises()

    expect(warningMessage).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/student/daily-feedback')
  })

  it('falls back to the dashboard for an unknown student route', async () => {
    getInbox.mockResolvedValueOnce({
      data: [{ ...unreadNotification, readAt: '2026-08-17T12:10:00', targetPath: '/student/unknown' }],
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.notification-row').trigger('click')
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/student/dashboard')
  })

  it('marks every unread item read from the header action', async () => {
    const wrapper = mountView()
    await flushPromises()

    const markAllButton = wrapper.findAll('button').find(button => button.text().includes('모두 읽음'))
    if (!markAllButton) throw new Error('모두 읽음 버튼을 찾을 수 없습니다')
    await markAllButton.trigger('click')
    await flushPromises()

    expect(markAllRead).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('새로 확인할 알림이 없어요')
    expect(wrapper.find('.notification-row').classes()).not.toContain('unread')
  })
})
