import { createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HandwrittenSolutionView from './HandwrittenSolutionView.vue'

const api = vi.hoisted(() => ({
  config: vi.fn(),
  read: vi.fn(),
  solve: vi.fn(),
  status: vi.fn(),
  image: vi.fn(),
  remove: vi.fn(),
}))
vi.mock('@/api/solutionNotebook', () => ({ solutionNotebookAPI: api }))
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
    vi.clearAllMocks()
    api.config.mockResolvedValue({
      enabled: false,
      mode: 'mock',
      fixture: 'rational-asymptote',
      dailySolutionLimit: 3,
    })
    api.remove.mockResolvedValue(undefined)

    revoke.mockClear()
    vi.stubGlobal(
      'fetch',
      vi.fn(() => {
        throw new Error('This prototype must not send requests')
      }),
    )
    URL.createObjectURL = vi.fn((value: Blob) =>
      value instanceof File ? 'blob:local-photo' : 'blob:mock-result',
    )
    URL.revokeObjectURL = revoke
  })
  it('자동 생성이 꺼져 있고 샘플을 열기 전에는 답을 표시하지 않는다', () => {
    const view = mountView()
    expect(view.find('textarea').exists()).toBe(false)
    expect(view.text()).not.toContain('문제와 풀이 설정')
    expect(view.text()).not.toContain('체험 상황')
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
  })
  it('사전 제작 샘플임을 표시하며 내 사진으로 바꾸면 기존 샘플 답을 숨긴다', async () => {
    const view = mountView()
    await clickSample(view)
    expect(view.get('.sample-disclaimer').text()).toContain(
      '새로 올린 사진을 분석한 결과가 아니에요',
    )
    expect(view.find('.sample-image-button').exists()).toBe(true)
    await choose(view, new File(['new'], 'new.png', { type: 'image/png' }))
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
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:local-photo')
  })
  it('내 사진에는 임의의 예제 문제나 해설을 채우지 않는다', async () => {
    api.config.mockResolvedValue({ enabled: true, mode: 'mock', fixture: 'rational-asymptote' })
    const view = mountView()
    await flushPromises()
    await choose(view, new File(['private'], 'my-problem.png', { type: 'image/png' }))
    expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
    expect(view.text()).toContain('이 사진은 아직 읽지 않았어요')
    expect(view.find('textarea').exists()).toBe(false)
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect(api.read).not.toHaveBeenCalled()
    expect(api.solve).not.toHaveBeenCalled()
    view.unmount()
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
  it('카메라 입력을 열고 촬영 결과를 기존 사진 검증과 미리보기로 처리한다', async () => {
    const view = mountView()
    const camera = view.get('input[capture="environment"]')
    const open = vi.spyOn(camera.element as HTMLInputElement, 'click')
    await view.get('.camera-button').trigger('click')
    expect(open).toHaveBeenCalledOnce()
    Object.defineProperty(camera.element, 'files', {
      value: [new File(['camera'], 'camera.jpg', { type: 'image/jpeg' })],
    })
    await camera.trigger('change')
    expect(view.get('.photo-preview img').attributes('src')).toBe('blob:local-photo')
    expect(view.get('[role="status"]').text()).toContain('camera.jpg')
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
  })
  it('복사된 이미지만 붙여넣어 샘플을 비우고 화면 종료 후 리스너를 제거한다', async () => {
    const view = mountView()
    await clickSample(view)
    const event = new Event('paste', { cancelable: true })
    Object.defineProperty(event, 'clipboardData', {
      value: {
        files: [],
        items: [
          {
            kind: 'file',
            type: 'image/png',
            getAsFile: () => new File(['paste'], 'clipboard.png', { type: 'image/png' }),
          },
        ],
      },
    })
    window.dispatchEvent(event)
    await flushPromises()
    expect(event.defaultPrevented).toBe(true)
    expect(view.get('.photo-toolbar').text()).toContain('clipboard.png')
    expect(view.find('.sample-image-button').exists()).toBe(false)
    expect(api.read).not.toHaveBeenCalled()
    expect(fetch).not.toHaveBeenCalled()
    view.unmount()
    const calls = vi.mocked(URL.createObjectURL).mock.calls.length
    window.dispatchEvent(event)
    expect(vi.mocked(URL.createObjectURL).mock.calls).toHaveLength(calls)
  })
  it('일반 텍스트와 문제 입력란의 붙여넣기는 가로채지 않는다', async () => {
    const view = mountView()
    const text = new Event('paste', { cancelable: true })
    Object.defineProperty(text, 'clipboardData', { value: { files: [], items: [] } })
    window.dispatchEvent(text)
    const editing = new Event('paste', { cancelable: true })
    Object.defineProperty(editing, 'target', { value: document.createElement('textarea') })
    Object.defineProperty(editing, 'clipboardData', {
      value: { files: [new File(['paste'], 'image.png', { type: 'image/png' })] },
    })
    window.dispatchEvent(editing)
    await flushPromises()
    expect(text.defaultPrevented).toBe(false)
    expect(editing.defaultPrevented).toBe(false)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    view.unmount()
  })
  it('한 번 누르면 예제 판독부터 이미지까지 자동 진행하며 내 사진은 전송하지 않는다', async () => {
    api.config.mockResolvedValue({
      enabled: true,
      mode: 'mock',
      fixture: 'rational-asymptote',
      dailySolutionLimit: 3,
    })
    const question = '유리함수 예제 문제'
    const read = {
      id: 'read-1',
      kind: 'READ',
      state: 'SUCCEEDED',
      mode: 'mock',
      question,
      uncertainties: [],
      message: '모의 판독 완료',
    }
    api.read.mockResolvedValue(read)
    api.solve.mockResolvedValue({
      id: 'solve-1',
      kind: 'SOLUTION',
      state: 'SUCCEEDED',
      mode: 'mock',
      question,
      solution: { title: '모의 풀이', steps: ['점근선을 확인'], answer: '(1,2)', diagram: '' },
      verified: true,
      imageAvailable: true,
      message: '모의 결과',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    api.image.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
    const view = mountView()
    await flushPromises()
    await choose(
      view,
      new File(['sensitive-photo-bytes'], 'private-name.png', { type: 'image/png' }),
    )
    await view.get('.mock-read-button').trigger('click')
    await flushPromises()
    expect(api.read).toHaveBeenCalledExactlyOnceWith('rational-asymptote', 'SUCCESS')
    expect(JSON.stringify(api.read.mock.calls)).not.toContain('private')
    expect(view.find('.confirm-question').exists()).toBe(false)
    expect(view.find('.photo-preview').exists()).toBe(false)
    expect(api.solve).toHaveBeenCalledWith(
      expect.objectContaining({ readJobId: 'read-1', confirmed: true, question }),
    )
    expect(api.image).toHaveBeenCalledWith('solve-1')
    expect(view.get('.sample-image-button img').attributes('src')).toBe('blob:mock-result')
    expect(view.text()).toContain('모의 API 결과')
    view.unmount()
    expect(revoke).toHaveBeenCalledWith('blob:mock-result')
  })
  it('이미지 검토 실패에는 다운로드 없이 풀이 텍스트와 실패 안내를 보여준다', async () => {
    api.config.mockResolvedValue({ enabled: true, mode: 'mock', fixture: 'rational-asymptote' })
    api.read.mockResolvedValue({
      id: 'read',
      state: 'SUCCEEDED',
      mode: 'mock',
      question: '예제',
      message: '모의 판독',
    })
    api.solve.mockResolvedValue({
      id: 'solve',
      state: 'REVIEW_REQUIRED',
      mode: 'mock',
      solution: { title: '모의 풀이', steps: ['확인'], answer: '(1,2)' },
      imageAvailable: false,
      verified: false,
      message: '모의 이미지 검토 실패',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    const view = mountView()
    await flushPromises()
    await view.get('.mock-read-button').trigger('click')
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('이미지 검토 실패')
    expect(api.image).not.toHaveBeenCalled()
    expect(view.find('a[download]').exists()).toBe(false)
    expect(view.get('.solution-text').text()).toContain('모의 풀이')
    view.unmount()
  })
  it('모의 API 오류는 재시도 안내로 표시하고 결과를 만들지 않는다', async () => {
    api.config.mockResolvedValue({ enabled: true, mode: 'mock', fixture: 'rational-asymptote' })
    api.read.mockRejectedValue({ response: { data: { message: '모의 사용량 초과' } } })
    const view = mountView()
    await flushPromises()
    await view.get('.mock-read-button').trigger('click')
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('사용량 초과')
    expect(view.find('a[download]').exists()).toBe(false)
    view.unmount()
  })
})
