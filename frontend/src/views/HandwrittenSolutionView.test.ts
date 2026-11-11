import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HandwrittenSolutionView from './HandwrittenSolutionView.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
const revoke = vi.fn()
function mountView() {
  return mount(HandwrittenSolutionView, {
    global: {
      plugins: [createPinia()],
      stubs: {
        ElIcon: { template: '<span><slot/></span>' },
        ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/></div>' },
      },
    },
  })
}
function clickSample(wrapper: ReturnType<typeof mountView>) {
  return wrapper
    .findAll('button')
    .find((b) => b.text() === '샘플 먼저 보기')!
    .trigger('click')
}
async function choose(wrapper: ReturnType<typeof mountView>, file: File) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('손글씨 풀이 시제품의 샘플 구분과 사진 수명', () => {
  beforeEach(() => {
    revoke.mockClear()
    vi.stubGlobal(
      'fetch',
      vi.fn(() => {
        throw new Error('This prototype must not send requests')
      }),
    )
    URL.createObjectURL = vi.fn(() => 'blob:local-photo')
    URL.revokeObjectURL = revoke
  })
  it('자동 생성이 꺼져 있고 샘플을 열기 전에는 답을 표시하지 않는다', () => {
    const view = mountView()
    expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
  })
  it('사전 제작 샘플임을 표시하며 문제를 편집하면 기존 샘플 답을 숨긴다', async () => {
    const view = mountView()
    await clickSample(view)
    expect(view.get('.sample-disclaimer').text()).toContain(
      '새로 올린 사진을 분석한 결과가 아니에요',
    )
    expect(view.find('.sample-image-button').exists()).toBe(true)
    await view.get('textarea').setValue('서로 다른 새 문제')
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect(view.find('a[download]').exists()).toBe(false)
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
  })
  it('새 사진을 선택하면 샘플 답을 비우고 화면 종료 시 사진 URL을 해제한다', async () => {
    const view = mountView()
    await clickSample(view)
    await choose(view, new File(['image'], 'problem.png', { type: 'image/png' }))
    expect(view.get('.photo-preview img').attributes('src')).toBe('blob:local-photo')
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect((view.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:local-photo')
  })
  it('잘못된 형식과 크기가 큰 사진을 거절하고 기존 사진을 보존한다', async () => {
    const view = mountView()
    await choose(view, new File(['image'], 'good.png', { type: 'image/png' }))
    await choose(view, new File(['text'], 'text.txt', { type: 'text/plain' }))
    expect(view.get('[role="alert"]').text()).toContain('JPG, PNG, WEBP')
    const large = new File(['image'], 'large.png', { type: 'image/png' })
    Object.defineProperty(large, 'size', { value: 13 * 1024 * 1024 })
    await choose(view, large)
    expect(view.get('[role="alert"]').text()).toContain('12MB 이하')
    expect(view.get('.photo-toolbar').text()).toContain('good.png')
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
  })
})
