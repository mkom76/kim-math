import { createPinia } from 'pinia'
import { computed, defineComponent, h, inject, provide, type ComputedRef } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import { dailyFeedbackAPI, type LessonStudentFeedback } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import LessonFeedbackReview from './LessonFeedbackReview.vue'

vi.mock('@/api/client', () => ({ dailyFeedbackAPI: { getLessonFeedbacks: vi.fn(), getDailyFeedback: vi.fn(), updateInstructorFeedback: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
const students: LessonStudentFeedback[] = [
  { studentId: 3, studentName: '가학생', lessonId: 10, instructorFeedback: 'AI 초안', feedbackAuthor: 'AI' },
  { studentId: 4, studentName: '나학생', lessonId: 10 },
]
const slot = { template: '<div><slot /></div>' }
const table = defineComponent({
  props: ['data'], setup(props, { slots }) {
    provide('rows', computed(() => props.data))
    return () => h('div', slots.default?.())
  },
})
const column = defineComponent({
  props: ['prop'], setup(props, { slots }) {
    const rows = inject<ComputedRef<LessonStudentFeedback[]>>('rows')!
    return () => h('div', rows.value.map((row, index) => slots.default?.({ row, $index: index }) || h('span', String(row[props.prop as keyof LessonStudentFeedback] || ''))))
  },
})
function mountReview() {
  const pinia = createPinia()
  useAuthStore(pinia).name = '담당 선생님'
  return mount(LessonFeedbackReview, { props: { lessonId: 10, refreshVersion: 0 }, global: {
    plugins: [pinia], directives: { loading: () => {} }, stubs: {
      ElTable: table, ElTableColumn: column, ElTag: slot, ElForm: slot, ElFormItem: slot, ElAlert: slot,
      ElDialog: { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h3>{{ title }}</h3><slot/><slot name="footer"/></section>' },
      ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot/></button>' },
      ElInput: { props: ['modelValue', 'disabled'], emits: ['update:modelValue'], template: '<textarea :disabled="disabled" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"/>' },
    },
  } })
}

describe('LessonFeedbackReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dailyFeedbackAPI.getLessonFeedbacks).mockResolvedValue({ data: structuredClone(students) } as never)
    vi.mocked(dailyFeedbackAPI.getDailyFeedback).mockImplementation(async studentId => ({ data: {
      lessonId: 10, lessonDate: '2026-10-20', instructorFeedback: studentId === 3 ? 'AI 초안' : '',
      todayHomework: { homeworkId: 20, homeworkTitle: '숙제', completion: 80, incorrectQuestions: '2,3' },
      todayTest: { testTitle: '시험', studentScore: 90, classAverage: 70, rank: 1, incorrectQuestions: [2], questionAccuracyRates: [] },
    } }) as never)
    vi.mocked(dailyFeedbackAPI.updateInstructorFeedback).mockResolvedValue({} as never)
  })

  it('reviews results, saves a human edit, and opens the next student in the same lesson', async () => {
    const wrapper = mountReview()
    await flushPromises()
    expect(wrapper.text()).toContain('작성 1 / 2명')
    await wrapper.findAll('[data-test="review-student"]')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('완성도 80%')
    expect(wrapper.text()).toContain('90점')
    await wrapper.get('textarea').setValue('검토한 피드백')
    await wrapper.get('[data-test="save-next-feedback"]').trigger('click')
    await flushPromises()
    expect(dailyFeedbackAPI.updateInstructorFeedback).toHaveBeenCalledExactlyOnceWith(3, 10, '검토한 피드백', '담당 선생님', false)
    expect(dailyFeedbackAPI.getDailyFeedback).toHaveBeenLastCalledWith(4, 10)
    expect(wrapper.text()).toContain('나학생 학생 피드백 (2/2)')
  })

  it('retains the draft and current student when saving fails', async () => {
    vi.mocked(dailyFeedbackAPI.updateInstructorFeedback).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountReview()
    await flushPromises()
    await wrapper.findAll('[data-test="review-student"]')[0]!.trigger('click')
    await flushPromises()
    await wrapper.get('textarea').setValue('저장되지 않은 수정')
    await wrapper.get('[data-test="save-next-feedback"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('저장되지 않은 수정')
    expect(dailyFeedbackAPI.getDailyFeedback).toHaveBeenCalledTimes(1)
  })

  it('keeps unsaved changes when switching or closing is cancelled', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    const wrapper = mountReview()
    await flushPromises()
    await wrapper.findAll('[data-test="review-student"]')[0]!.trigger('click')
    await flushPromises()
    await wrapper.get('textarea').setValue('수정 중')
    await wrapper.findAll('button').find(button => button.text() === '다음 학생')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '닫기')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('수정 중')
    expect(dailyFeedbackAPI.getDailyFeedback).toHaveBeenCalledTimes(1)
  })

  it('disables saving after a detail load failure and refreshes the roster after bulk generation', async () => {
    vi.mocked(dailyFeedbackAPI.getDailyFeedback).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountReview()
    await flushPromises()
    await wrapper.findAll('[data-test="review-student"]')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="save-next-feedback"]').attributes('disabled')).toBeDefined()
    await wrapper.setProps({ refreshVersion: 1 })
    await flushPromises()
    expect(dailyFeedbackAPI.getLessonFeedbacks).toHaveBeenCalledTimes(2)
    expect(dailyFeedbackAPI.updateInstructorFeedback).not.toHaveBeenCalled()
  })
})
