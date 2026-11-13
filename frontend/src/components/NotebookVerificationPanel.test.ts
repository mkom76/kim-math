import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Panel from './NotebookVerificationPanel.vue'
import type { NotebookVerification } from '@/api/solutionNotebook'
const mismatch: NotebookVerification = {
  stage: 'IMAGE',
  verdict: 'MISMATCH',
  issues: [
    {
      category: 'SIGN',
      location: '두 번째 줄',
      expected: 'y = −3',
      observed: 'y = 3',
      reason: '음수 부호가 누락됐습니다.',
    },
  ],
}
describe('수식 검증 상세', () => {
  it('차이의 위치와 기준·관측 수식을 구분하고 AI 판정임을 알린다', () => {
    const w = mount(Panel, { props: { report: mismatch } })
    expect(w.text()).toContain('손글씨 이미지 검증 내용')
    for (const text of ['두 번째 줄', 'y = −3', 'y = 3', '음수 부호가 누락', 'AI의 비교 결과'])
      expect(w.text()).toContain(text)
  })
  it('판독 불확실성을 오류 확정으로 표현하거나 관측 수식을 지어내지 않는다', () => {
    const w = mount(Panel, {
      props: {
        report: {
          ...mismatch,
          stage: 'MATH',
          verdict: 'UNCERTAIN',
          issues: [{ ...mismatch.issues[0]!, observed: '' }],
        },
      },
    })
    expect(w.text()).toContain('문제·풀이 검증 내용')
    expect(w.text()).toContain('정확하게 판단하기 어려워요')
    expect(w.text()).toContain('선명하게 읽지 못했어요')
  })
  it('서비스 장애·응답 오류는 수식 오류로 확정하지 않는다', () => {
    for (const verdict of ['UNAVAILABLE', 'INVALID_RESPONSE'] as const) {
      const w = mount(Panel, { props: { report: { stage: 'IMAGE', verdict, issues: [] } } })
      expect(w.text()).toContain('수식 오류로 확정한 결과는 아닙니다')
      expect(w.findAll('li')).toHaveLength(0)
    }
  })
  it('진단 문자열은 HTML로 실행하지 않고 통과·초기화 시 상세를 숨긴다', async () => {
    const w = mount(Panel, {
      props: {
        report: {
          ...mismatch,
          issues: [{ ...mismatch.issues[0]!, reason: '<img src=x onerror=alert(1)>' }],
        },
      },
    })
    expect(w.find('img').exists()).toBe(false)
    expect(w.text()).toContain('<img src=x')
    await w.setProps({ report: { stage: 'IMAGE', verdict: 'PASSED', issues: [] } })
    expect(w.find('section').exists()).toBe(false)
    await w.setProps({ report: null })
    expect(w.find('section').exists()).toBe(false)
  })
})
