import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import StudentBottomNavV2 from './StudentBottomNavV2.vue'
import StudentMoreView from '@/views/student-v2/StudentMoreView.vue'

const { push, routeState } = vi.hoisted(() => ({
  push: vi.fn(),
  routeState: { path: '/student/dashboard' },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push }),
}))

const iconStub = { template: '<span><slot/></span>' }

describe('student V2 navigation', () => {
  beforeEach(() => {
    push.mockReset()
    routeState.path = '/student/dashboard'
  })

  it('places the More tab at the far right', async () => {
    const wrapper = mount(StudentBottomNavV2, {
      global: { stubs: { ElIcon: iconStub } },
    })

    expect(wrapper.findAll('.tab').map(tab => tab.text())).toEqual(['홈', '시험', '클리닉', '더보기'])
    const moreTab = wrapper.findAll('.tab')[3]
    if (!moreTab) throw new Error('더보기 탭을 찾을 수 없습니다')
    await moreTab.trigger('click')
    expect(push).toHaveBeenCalledWith('/student/more')
  })

  it('shows four statistics destinations as full menu rows', async () => {
    const wrapper = mount(StudentMoreView, {
      global: { stubs: { ElIcon: iconStub } },
    })

    const statisticsSection = wrapper.find('[aria-labelledby="more-statistics-title"]')
    const rows = statisticsSection.findAll('.more-menu-row')
    expect(rows.map(row => row.find('strong').text())).toEqual([
      '시험 통계',
      '숙제 통계',
      '출석 통계',
      '영상 통계',
    ])

    const attendanceRow = rows[2]
    if (!attendanceRow) throw new Error('출석 통계 메뉴를 찾을 수 없습니다')
    await attendanceRow.trigger('pointerdown')
    expect(push).not.toHaveBeenCalled()

    await attendanceRow.trigger('pointerup')
    expect(push).not.toHaveBeenCalled()

    await attendanceRow.trigger('click')
    expect(push).toHaveBeenCalledWith('/student/statistics/attendance')
  })
})
