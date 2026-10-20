import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { Submission } from '@/api/client'
import ErrorCauseTrends from './ErrorCauseTrends.vue'

const records: Submission[] = [5, null, 0].map((value, index) => ({
  id: index + 1,
  testId: index + 1,
  testTitle: `${index + 1}회차`,
  classId: 1,
  className: '고1 A반',
  lessonDate: `2026-10-${12 + index}`,
  totalScore: 80,
  errorCauses: {
    calculationMistake: value,
    conceptGap: null,
    conditionMisread: null,
    ideaGap: null,
    timeManagement: null,
    status: value === null ? 'NOT_ENTERED' : 'PARTIAL',
  },
}))

describe('ErrorCauseTrends', () => {
  it('renders zero separately from missing and leaves the chart gap unconnected', () => {
    const wrapper = mount(ErrorCauseTrends, { props: { submissions: records } })
    expect(wrapper.findAll('circle')).toHaveLength(2)
    expect(wrapper.findAll('polyline')).toHaveLength(0)
    const rows = wrapper.findAll('tbody tr')
    expect(rows[1]!.text()).toContain('미입력')
    expect(rows[2]!.text()).toContain('0건')
    expect(rows[2]!.text()).toContain('비교 불가')
  })

  it('never mixes classes and lets the teacher switch the comparison class', async () => {
    const otherClass: Submission = {
      ...records[0]!,
      id: 9,
      testId: 9,
      classId: 2,
      className: '고2 B반',
      testTitle: '다른 반 시험',
      lessonDate: '2026-10-20',
    }
    const wrapper = mount(ErrorCauseTrends, { props: { submissions: [...records, otherClass] } })
    expect(wrapper.get('tbody').text()).toContain('다른 반 시험')
    expect(wrapper.get('tbody').text()).not.toContain('1회차')
    await wrapper.get('select').setValue('1')
    expect(wrapper.findAll('tbody tr')).toHaveLength(3)
    expect(wrapper.get('tbody').text()).not.toContain('다른 반 시험')
  })

  it('toggles visible chart causes without hiding the recorded table values', async () => {
    const wrapper = mount(ErrorCauseTrends, { props: { submissions: records } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.findAll('circle')).toHaveLength(0)
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')
    expect(wrapper.get('table').text()).toContain('5건')
    expect(wrapper.get('table').text()).toContain('0건')
  })
})
