import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { academyClassAPI, homeworkAPI, lessonAPI } from '@/api/client'
import HomeworkLessonAssignmentDialog from './HomeworkLessonAssignmentDialog.vue'

vi.mock('@/api/client', () => ({ homeworkAPI: { getAssignedLessons: vi.fn(), assignToLessons: vi.fn() }, lessonAPI: { getLessons: vi.fn() }, academyClassAPI: { getAcademyClasses: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
const slot = { template: '<div><slot/></div>' }
function mountDialog() {
  return mount(HomeworkLessonAssignmentDialog, { props: { visible: true, homework: { id: 5, title: '공통 숙제', academyId: 1, questionCount: 10 } }, global: {
    directives: { loading: () => {} }, stubs: {
      ElDialog: { template: '<section><slot/><slot name="footer"/></section>' }, ElAlert: slot, ElForm: slot, ElFormItem: slot, ElTag: slot,
      ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot/></button>' },
      ElSelect: { props: ['modelValue'], emits: ['update:modelValue'], template: '<select multiple :value="modelValue" @change="$emit(\'update:modelValue\', Array.from($event.target.selectedOptions, option => Number(option.value)))"><slot/></select>' },
      ElOption: { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
      ElCheckbox: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)"/>' },
    },
  } })
}

describe('HomeworkLessonAssignmentDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(lessonAPI.getLessons).mockResolvedValue({ data: { content: [
      { id: 10, academyId: 1, classId: 11, className: '기본반', lessonDate: '2026-10-20' },
      { id: 20, academyId: 1, classId: 12, className: '심화반', lessonDate: '2026-10-21' },
      { id: 30, academyId: 1, classId: 13, className: '종강반', lessonDate: '2026-10-22' },
      { id: 40, academyId: 2, classId: 14, className: '다른 학원', lessonDate: '2026-10-22' },
    ] } } as never)
    vi.mocked(academyClassAPI.getAcademyClasses).mockResolvedValue({ data: { content: [
      { id: 11 }, { id: 12 }, { id: 13, ended: true }, { id: 14 },
    ] } } as never)
    vi.mocked(homeworkAPI.getAssignedLessons).mockResolvedValue({ data: [] } as never)
    vi.mocked(homeworkAPI.assignToLessons).mockResolvedValue({ data: [] } as never)
  })

  it('assigns one homework to multiple active lessons in one request', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(wrapper.findAll('option').map(option => option.text())).toEqual(['2026-10-21 · 심화반', '2026-10-20 · 기본반'])
    await wrapper.get('select').setValue(['10', '20'])
    await wrapper.get('[data-test="assign-lessons"]').trigger('click')
    await flushPromises()
    expect(homeworkAPI.assignToLessons).toHaveBeenCalledExactlyOnceWith(5, [20, 10], true)
    expect(wrapper.emitted('assigned')).toHaveLength(1)
    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })

  it('excludes already assigned lessons and can link without assigning students', async () => {
    vi.mocked(homeworkAPI.getAssignedLessons).mockResolvedValueOnce({ data: [{ id: 10, lessonDate: '2026-10-20', className: '기본반' }] } as never)
    const wrapper = mountDialog()
    await flushPromises()
    expect(wrapper.findAll('option').map(option => option.attributes('value'))).toEqual(['20'])
    await wrapper.get('select').setValue(['20'])
    await wrapper.get('input[type="checkbox"]').setValue(false)
    await wrapper.get('[data-test="assign-lessons"]').trigger('click')
    await flushPromises()
    expect(homeworkAPI.assignToLessons).toHaveBeenCalledWith(5, [20], false)
  })

  it('keeps the selected lessons after a failed request', async () => {
    vi.mocked(homeworkAPI.assignToLessons).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountDialog()
    await flushPromises()
    await wrapper.get('select').setValue(['20'])
    await wrapper.get('[data-test="assign-lessons"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(wrapper.get('select').element.selectedOptions[0]?.value).toBe('20')
    expect(wrapper.get('[data-test="assign-lessons"]').attributes('disabled')).toBeUndefined()
  })
})
