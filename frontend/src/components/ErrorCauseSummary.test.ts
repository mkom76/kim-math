import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorCauseSummary from './ErrorCauseSummary.vue'

describe('ErrorCauseSummary', () => {
  it('shows five zero counts for a missing record', () => {
    const wrapper = mount(ErrorCauseSummary)
    expect(wrapper.findAll('dd').map(item => item.text())).toEqual(Array(5).fill('0건'))
    expect(wrapper.text()).toContain('미입력은 0건으로 표시합니다')
  })

  it('shows missing legacy counts as zero while preserving the review warning', () => {
    const wrapper = mount(ErrorCauseSummary, { props: { value: {
      calculationMistake: 2, conceptGap: null, conditionMisread: null, ideaGap: 0,
      timeManagement: null, status: 'NEEDS_REVIEW',
    } } })
    expect(wrapper.findAll('dd').map(item => item.text())).toEqual(['2건', '0건', '0건', '0건', '0건'])
    expect(wrapper.text()).toContain('선생님의 재확인이 필요합니다')
  })
})
