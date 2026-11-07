import { ref } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authAPI, submissionAPI, testAPI } from '@/api/client'
import StudentTestTakingView from './StudentTestTakingView.vue'
import StudentTestTakingViewV2 from './student-v2/StudentTestTakingView.vue'

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '10' } }), useRouter: () => ({ push }) }))
vi.mock('@/composables/useBreakpoint', () => ({ useBreakpoint: () => ({ isMobile: ref(false) }) }))
vi.mock('@/api/client', () => ({
  authAPI: { getCurrentUser: vi.fn() },
  testAPI: { getTest: vi.fn(), getTestQuestions: vi.fn() },
  submissionAPI: { submitMyAnswers: vi.fn() },
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() },
}))

const updatedAt = '2026-11-07T20:00:00'
const message = '시험 문항이 변경되었습니다. 새로고침 후 다시 시도해주세요'

describe('student submission question snapshots', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(authAPI.getCurrentUser).mockResolvedValue({ data: { name: '학생' } } as never)
    vi.mocked(testAPI.getTest).mockResolvedValue({ data: { title: '시험' } } as never)
    vi.mocked(testAPI.getTestQuestions).mockResolvedValue({ data: [
      { id: 101, number: 1, points: 100, questionType: 'SUBJECTIVE', updatedAt },
    ] } as never)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    vi.mocked(submissionAPI.submitMyAnswers).mockRejectedValue({
      isAxiosError: true, response: { status: 409, data: { message } },
    })
  })

  it.each([['original', StudentTestTakingView], ['v2', StudentTestTakingViewV2]] as const)(
    '%s sends the loaded snapshot and asks to refresh without leaving the exam on conflict', async (_name, view) => {
      const wrapper = shallowMount(view, { global: {
        directives: { loading: () => {} },
        stubs: {
          ...Object.fromEntries([
            'ElIcon', 'ElAlert', 'ElTag', 'ElCheckboxGroup', 'ElCheckboxButton', 'ElRadioGroup', 'ElRadioButton',
            'Select', 'Edit', 'Memo', 'Close', 'InfoFilled', 'VideoPlay', 'WarningFilled',
          ].map(name => [name, true])),
          ElCard: { template: '<section><slot name="header" /><slot /></section>' },
          ElButton: { template: '<button><slot /></button>' },
          ElInput: {
            props: ['modelValue'], emits: ['update:modelValue'],
            template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          },
        },
      } })
      await flushPromises()
      await wrapper.get('input').setValue('3')
      await wrapper.findAll('button').find(button => button.text().endsWith('제출하기'))!.trigger('click')
      await flushPromises()

      expect(submissionAPI.submitMyAnswers).toHaveBeenCalledWith(10, { 1: '3' }, [
        { id: 101, number: 1, updatedAt },
      ])
      expect(ElMessage.error).toHaveBeenCalledWith(message)
      expect(push).not.toHaveBeenCalled()
      expect(wrapper.get('input').element).toHaveProperty('value', '3')
      wrapper.unmount()
    },
  )
})
