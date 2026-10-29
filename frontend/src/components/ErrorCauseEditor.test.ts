import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { submissionAPI, type ErrorCauses } from '@/api/client'
import ErrorCauseEditor from './ErrorCauseEditor.vue'

vi.mock('@/api/client', () => ({ submissionAPI: { saveErrorCauses: vi.fn() } }))
const saved: ErrorCauses = {
  calculationMistake: 0,
  conceptGap: 0,
  conditionMisread: 0,
  ideaGap: 0,
  timeManagement: 0,
  status: 'COMPLETE',
}

describe('ErrorCauseEditor', () => {
  beforeEach(() => vi.clearAllMocks())

  it('defaults to zero and saves cleared or blank fields as zero', async () => {
    vi.mocked(submissionAPI.saveErrorCauses).mockResolvedValue({ data: saved } as never)
    const wrapper = mount(ErrorCauseEditor, { props: { submissionId: 42 } })
    expect(wrapper.findAll('input').every(input => (input.element as HTMLInputElement).value === '0')).toBe(true)
    await wrapper.get('input[aria-label="계산 실수 건수"]').setValue('')
    await wrapper.get('input[aria-label="개념 부족 건수"]').setValue('  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(submissionAPI.saveErrorCauses).toHaveBeenCalledWith(42, {
      calculationMistake: 0,
      conceptGap: 0,
      conditionMisread: 0,
      ideaGap: 0,
      timeManagement: 0,
    })
    expect(wrapper.emitted('saved')).toEqual([[saved]])
  })

  it.each(['-1', '1.5', '2147483648'])('rejects invalid count %s before saving', async (value) => {
    const wrapper = mount(ErrorCauseEditor, { props: { submissionId: 42 } })
    await wrapper.get('input').setValue(value)
    await wrapper.get('form').trigger('submit')
    expect(submissionAPI.saveErrorCauses).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('정수')
  })

  it('clears a stale validation message when the teacher corrects the input', async () => {
    const wrapper = mount(ErrorCauseEditor, { props: { submissionId: 42 } })
    await wrapper.get('input').setValue('-1')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await wrapper.get('input').setValue('5')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('preserves input after a failed save and allows retry', async () => {
    vi.mocked(submissionAPI.saveErrorCauses)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { ...saved, calculationMistake: 7 } } as never)
    const wrapper = mount(ErrorCauseEditor, { props: { submissionId: 42, initialValue: saved } })
    await wrapper.get('input').setValue('7')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('저장하지 못했습니다')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('7')
    expect(wrapper.emitted('saved')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('prevents duplicate submissions while saving', async () => {
    let resolve!: (response: never) => void
    vi.mocked(submissionAPI.saveErrorCauses).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mount(ErrorCauseEditor, { props: { submissionId: 42 } })
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(submissionAPI.saveErrorCauses).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    resolve({ data: saved } as never)
    await flushPromises()
  })
})
