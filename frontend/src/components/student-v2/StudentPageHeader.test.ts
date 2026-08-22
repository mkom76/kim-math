import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StudentPageHeader from './StudentPageHeader.vue'

describe('StudentPageHeader', () => {
  it('renders shared page copy and optional slots', () => {
    const wrapper = mount(StudentPageHeader, {
      props: {
        eyebrow: 'MY STUDY',
        title: '학습 현황',
        subtitle: '최근 기록을 확인해요.',
        titleId: 'page-title',
      },
      slots: {
        meta: '<p class="meta">학생 정보</p>',
        action: '<button type="button">알림</button>',
      },
    })

    expect(wrapper.get('#page-title').text()).toBe('학습 현황')
    expect(wrapper.get('.student-page__eyebrow').text()).toBe('MY STUDY')
    expect(wrapper.get('.student-page__subtitle').text()).toBe('최근 기록을 확인해요.')
    expect(wrapper.get('.meta').text()).toBe('학생 정보')
    expect(wrapper.get('button').text()).toBe('알림')
  })
})
