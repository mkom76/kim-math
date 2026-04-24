import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import QuestionedQuestionsPicker from './QuestionedQuestionsPicker.vue'

const buttonsOf = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('[data-test="question-number"]')

const selected = (wrapper: ReturnType<typeof mount>) =>
  buttonsOf(wrapper)
    .filter(b => b.classes().includes('is-selected'))
    .map(b => b.text())

describe('QuestionedQuestionsPicker', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders questionCount buttons numbered 1..N', () => {
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 5, initialValue: null, onSave: vi.fn() },
    })

    expect(buttonsOf(wrapper).map(b => b.text())).toEqual(['1', '2', '3', '4', '5'])
  })

  it('preselects buttons from initialValue', () => {
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 10, initialValue: '3,7,9', onSave: vi.fn() },
    })

    expect(selected(wrapper).sort()).toEqual(['3', '7', '9'])
  })

  it('toggles selection on click immediately', async () => {
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 5, initialValue: '2', onSave: vi.fn().mockResolvedValue(undefined) },
    })

    await buttonsOf(wrapper)[2]!.trigger('click') // click "3"
    expect(selected(wrapper).sort()).toEqual(['2', '3'])

    await buttonsOf(wrapper)[1]!.trigger('click') // click "2" → deselect
    expect(selected(wrapper)).toEqual(['3'])
  })

  it('debounces onSave: rapid clicks → one call with final value', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 10, initialValue: null, onSave },
    })

    await buttonsOf(wrapper)[0]!.trigger('click') // 1
    await buttonsOf(wrapper)[2]!.trigger('click') // 3
    await buttonsOf(wrapper)[6]!.trigger('click') // 7

    expect(onSave).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(500)

    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave).toHaveBeenCalledWith('1,3,7')
  })

  it('rolls back to last saved state when onSave rejects', async () => {
    const onSave = vi.fn().mockRejectedValue(new Error('network'))
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 5, initialValue: '1', onSave },
    })

    await buttonsOf(wrapper)[1]!.trigger('click') // add 2 — now [1, 2]
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    expect(onSave).toHaveBeenCalledWith('1,2')
    // Rolled back to initial saved value '1'
    expect(selected(wrapper)).toEqual(['1'])
  })

  it('does not call onSave if value did not change after debounce', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(QuestionedQuestionsPicker, {
      props: { questionCount: 5, initialValue: '1', onSave },
    })

    // Toggle 2 on then off → net same as initial
    await buttonsOf(wrapper)[1]!.trigger('click')
    await buttonsOf(wrapper)[1]!.trigger('click')

    await vi.advanceTimersByTimeAsync(500)

    expect(onSave).not.toHaveBeenCalled()
  })
})
