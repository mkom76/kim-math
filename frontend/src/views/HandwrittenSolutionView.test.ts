import { createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HandwrittenSolutionView from './HandwrittenSolutionView.vue'

const api = vi.hoisted(() => ({
  config: vi.fn(),
  liveConfig: vi.fn(),
  livePhoto: vi.fn(),
  liveStatus: vi.fn(),
  liveImage: vi.fn(),
  liveRemove: vi.fn(),
  read: vi.fn(),
  solve: vi.fn(),
  status: vi.fn(),
  image: vi.fn(),
  remove: vi.fn(),
  trialConfig: vi.fn(),
  photo: vi.fn(),
  photoStatus: vi.fn(),
  photoImage: vi.fn(),
  photoRemove: vi.fn(),
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
    api.liveConfig.mockResolvedValue({ enabled: false, ready: false })
    api.liveRemove.mockResolvedValue(undefined)
    api.remove.mockResolvedValue(undefined)
    api.trialConfig.mockResolvedValue({ ready: false, used: false, mode: 'live', notice: '' })
    api.photoRemove.mockResolvedValue(undefined)

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
  it('질문 전에도 하루 3회 한도와 한국 시간 밤 12시 초기화를 안내한다', async () => {
    api.liveConfig.mockResolvedValue({
      enabled: true,
      ready: true,
      dailyLimit: 3,
      remainingToday: 3,
      dailyLimitReached: false,
    })
    const view = mountView()
    await flushPromises()
    expect(view.get('.question-quota').text()).toContain('하루 최대 3번 질문할 수 있어요.')
    expect(view.get('.question-quota').text()).toContain('매일 밤 12시(한국 시간)에 초기화돼요.')
    expect(view.get('.question-quota').text()).toContain('오늘 남은 질문 3 / 3회')
    expect(view.find('.daily-limit-notice').exists()).toBe(false)
    expect(api.livePhoto).not.toHaveBeenCalled()
    view.unmount()
  })
  it('3회 소진 후 추가 사진에는 자정 안내를 표시하고 새 AI 요청을 막는다', async () => {
    api.liveConfig.mockResolvedValue({
      enabled: true,
      ready: false,
      dailyLimit: 3,
      remainingToday: 0,
      dailyLimitReached: true,
    })
    const view = mountView()
    await flushPromises()
    await choose(view, new File(['new'], 'another.png', { type: 'image/png' }))
    expect(view.get('.daily-limit-notice').attributes('role')).toBe('status')
    expect(view.get('.daily-limit-notice').text()).toContain('오늘 질문 3번을 모두 사용했어요.')
    expect(view.get('.daily-limit-notice').text()).toContain(
      '밤 12시(한국 시간)에 횟수가 초기화되면 다시 질문할 수 있어요.',
    )
    expect(view.get('.automatic-button').text()).toBe('밤 12시에 다시 질문할 수 있어요')
    expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
    expect(api.livePhoto).not.toHaveBeenCalled()
    view.unmount()
  })
  it('서버 준비 중인 상태를 개인 질문 한도 소진으로 안내하지 않는다', async () => {
    api.liveConfig.mockResolvedValue({
      enabled: true,
      ready: false,
      dailyLimit: 3,
      remainingToday: 0,
      dailyLimitReached: false,
      notice: '서버 준비 중',
    })
    const view = mountView()
    await flushPromises()
    expect(view.find('.daily-limit-notice').exists()).toBe(false)
    expect(view.text()).not.toContain('오늘 남은 질문 0')
    expect(view.text()).toContain('서버 준비 중')
    view.unmount()
  })
  it('페이지를 열어둔 채 한국 시간 자정을 지나면 남은 질문을 다시 확인한다', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(new Date('2026-11-12T14:59:59.500Z'))
    api.liveConfig
      .mockResolvedValueOnce({
        enabled: true,
        ready: false,
        dailyLimit: 3,
        remainingToday: 0,
        dailyLimitReached: true,
      })
      .mockResolvedValue({
        enabled: true,
        ready: true,
        dailyLimit: 3,
        remainingToday: 3,
        dailyLimitReached: false,
      })
    const view = mountView()
    try {
      await flushPromises()
      expect(view.find('.daily-limit-notice').exists()).toBe(true)
      await vi.advanceTimersByTimeAsync(1500)
      await flushPromises()
      expect(api.liveConfig).toHaveBeenCalledTimes(2)
      expect(view.find('.daily-limit-notice').exists()).toBe(false)
      expect(view.get('.question-quota').text()).toContain('오늘 남은 질문 3 / 3회')
      view.unmount()
      await vi.advanceTimersByTimeAsync(86400000)
      expect(api.liveConfig).toHaveBeenCalledTimes(2)
    } finally {
      view.unmount()
      vi.useRealTimers()
    }
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
      verification: {
        stage: 'IMAGE',
        verdict: 'MISMATCH',
        issues: [
          {
            category: 'FRACTION',
            location: '두 번째 줄',
            expected: '2/(x−2)',
            observed: '2/x−2',
            reason: '분모의 괄호가 빠졌습니다.',
          },
        ],
      },
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
    expect(view.get('[aria-label="검증 상세"]').text()).toContain('분모의 괄호가 빠졌습니다')
    await choose(view, new File(['new'], 'new.png', { type: 'image/png' }))
    expect(view.find('[aria-label="검증 상세"]').exists()).toBe(false)
    view.unmount()
  })
  it('수학 검증 실패에서도 진단을 보여주고 만료되면 비운다', async () => {
    vi.useFakeTimers()
    try {
      api.config.mockResolvedValue({ enabled: true, mode: 'mock', fixture: 'rational-asymptote' })
      api.read.mockResolvedValue({
        id: 'read',
        state: 'SUCCEEDED',
        mode: 'mock',
        question: '가상 문제',
      })
      api.solve.mockResolvedValue({
        id: 'solve',
        state: 'FAILED',
        mode: 'mock',
        question: '가상 문제',
        solution: null,
        imageAvailable: false,
        verified: false,
        message: '수학 검증 중단',
        expiresAt: new Date(Date.now() + 1000).toISOString(),
        verification: {
          stage: 'MATH',
          verdict: 'UNCERTAIN',
          issues: [
            {
              category: 'CONDITION',
              location: '첫 줄',
              expected: 'x>0',
              observed: '',
              reason: '부등호를 확인하기 어렵습니다.',
            },
          ],
        },
      })
      const view = mountView()
      await flushPromises()
      await view.get('.mock-read-button').trigger('click')
      await flushPromises()
      expect(view.get('[aria-label="검증 상세"]').text()).toContain('부등호를 확인하기 어렵습니다')
      expect(api.image).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1001)
      expect(view.find('[aria-label="검증 상세"]').exists()).toBe(false)
      view.unmount()
    } finally {
      vi.useRealTimers()
    }
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
  it('실제 API는 샘플 전송 동의 후 사진만 보내며 실제 결과로 표시한다', async () => {
    api.trialConfig
      .mockResolvedValueOnce({ ready: true, used: false, mode: 'live', notice: '샘플 시험' })
      .mockResolvedValue({ ready: false, used: true, mode: 'live', notice: '' })
    const decode = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'decode')
    Object.defineProperty(HTMLImageElement.prototype, 'decode', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    })
    const context = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '',
      fillRect: vi.fn(),
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D)
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, 'toBlob')
      .mockImplementation((callback) =>
        callback(new Blob(['normalized-pixels'], { type: 'image/jpeg' })),
      )
    api.photo.mockResolvedValue({
      id: 'real-1',
      mode: 'live',
      state: 'SUCCEEDED',
      question: '새 문제',
      solution: { title: '실제 풀이', steps: ['단계'], answer: '2' },
      imageAvailable: true,
      verified: true,
      message: '사진으로 만든 해설',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    api.photoImage.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
    const view = mountView()
    try {
      await flushPromises()
      await choose(
        view,
        new File(['private-original'], 'private-file-name.png', { type: 'image/png' }),
      )
      expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
      expect(api.photo).not.toHaveBeenCalled()
      await view.get('.confirm-question input').setValue(true)
      await view.get('.automatic-button').trigger('click')
      await flushPromises()
      expect(api.photo).toHaveBeenCalledExactlyOnceWith(expect.any(Blob), 0)
      expect(api.photo.mock.calls[0]?.[0]?.type).toBe('image/jpeg')
      expect(api.read).not.toHaveBeenCalled()
      expect(api.solve).not.toHaveBeenCalled()
      expect(view.text()).toContain('실제 AI 시험 결과')
      expect(view.get('a[download]').attributes('download')).toBe('김수학_AI해설.png')
      expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
    } finally {
      view.unmount()
      context.mockRestore()
      toBlob.mockRestore()
      if (decode) Object.defineProperty(HTMLImageElement.prototype, 'decode', decode)
      else Reflect.deleteProperty(HTMLImageElement.prototype, 'decode')
    }
    expect(api.photoRemove).toHaveBeenCalledWith('real-1')
  })
  it('사이트에서는 명시적인 전송 확인 후 전용 API로 사진과 회전값만 보내고 이미지를 받는다', async () => {
    api.liveConfig.mockResolvedValue({
      enabled: true,
      ready: true,
      preview: false,
      dailyLimit: 2,
      remainingToday: 2,
      retentionMinutes: 10,
      notice: '사진 전송 안내',
    })
    const decode = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'decode')
    Object.defineProperty(HTMLImageElement.prototype, 'decode', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    })
    const context = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '',
      fillRect: vi.fn(),
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D)
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, 'toBlob')
      .mockImplementation((callback) =>
        callback(new Blob(['normalized-pixels'], { type: 'image/jpeg' })),
      )
    api.livePhoto.mockResolvedValue({
      id: 'real-1',
      mode: 'live',
      state: 'SUCCEEDED',
      question: '새 문제',
      solution: { title: '실제 풀이', steps: ['단계'], answer: '2' },
      imageAvailable: true,
      verified: true,
      message: '사진으로 만든 해설',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    api.liveImage.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
    const view = mountView()
    try {
      await flushPromises()
      await choose(
        view,
        new File(['private-original'], 'private-file-name.png', { type: 'image/png' }),
      )
      expect(view.get('.automatic-button').attributes('disabled')).toBeDefined()
      expect(api.livePhoto).not.toHaveBeenCalled()
      await view.get('.confirm-question input').setValue(true)
      await view.get('.automatic-button').trigger('click')
      await flushPromises()
      expect(api.livePhoto).toHaveBeenCalledExactlyOnceWith(expect.any(Blob), 0)
      expect(api.livePhoto.mock.calls[0]?.[0]?.type).toBe('image/jpeg')
      expect(api.read).not.toHaveBeenCalled()
      expect(api.solve).not.toHaveBeenCalled()
      expect(view.text()).toContain('사진으로 만든 AI 해설')
      expect(view.find('.mock-read-button').exists()).toBe(false)
      expect(api.photo).not.toHaveBeenCalled()
      expect(view.get('.read-question').text()).toContain('새 문제')
      expect(view.get('a[download]').attributes('download')).toBe('김수학_AI해설.png')
      expect(api.liveImage).toHaveBeenCalledWith('real-1')
    } finally {
      view.unmount()
      context.mockRestore()
      toBlob.mockRestore()
      if (decode) Object.defineProperty(HTMLImageElement.prototype, 'decode', decode)
      else Reflect.deleteProperty(HTMLImageElement.prototype, 'decode')
    }
    expect(api.liveRemove).toHaveBeenCalledWith('real-1')
  })
})
