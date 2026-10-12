import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { studentAPI } from '@/api/client'
import StudentBulkImportDialog from './StudentBulkImportDialog.vue'

vi.mock('@/api/client', () => ({ studentAPI: { bulkCreate: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

const writeText = vi.fn().mockResolvedValue(undefined)
const slotStub = { template: '<div><slot /></div>' }

describe('StudentBulkImportDialog registration results', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
  })

  it.each([null, 'parent-token'])('copies useful registration details for consent token %s', async (consentToken) => {
    vi.mocked(studentAPI.bulkCreate).mockResolvedValue({ data: { created: [
      { studentId: 7, name: '김학생', parentName: '보호자', parentPhone: '010-1234-5678', consentToken },
    ] } } as never)
    const wrapper = mount(StudentBulkImportDialog, {
      props: { visible: true, classes: [{ id: 11, name: '기본반', academyId: 1 }], existingNames: [] },
      global: { stubs: {
        ElDialog: { template: '<section><slot /><slot name="footer" /></section>' },
        ElForm: slotStub, ElFormItem: slotStub, ElTag: slotStub,
        ElAlert: { props: ['description'], template: '<div>{{ description }}<slot /></div>' },
        ElSelect: {
          props: ['modelValue'], emits: ['update:modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
        },
        ElOption: { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
        ElInput: {
          props: ['modelValue'], emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        ElButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
        ElTable: true,
      } },
    })
    await wrapper.get('select').setValue('11')
    await wrapper.get('textarea').setValue('김학생\t고1\tA고\t보호자\t010-1234-5678')
    await wrapper.findAll('button').find(button => button.text() === '1명 등록')!.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('imported')).toHaveLength(1)
    const copyButton = wrapper.findAll('button').find(button => button.text().startsWith('전체 명단'))!
    await copyButton.trigger('click')
    if (consentToken) {
      expect(copyButton.text()).toBe('전체 명단·링크 복사')
      expect(writeText).toHaveBeenCalledWith(`김학생 (학부모 보호자 010-1234-5678)\n${window.location.origin}/consent/parent-token`)
    } else {
      expect(wrapper.text()).toContain('바로 로그인할 수 있습니다')
      expect(copyButton.text()).toBe('전체 명단·로그인 정보 복사')
      expect(writeText).toHaveBeenCalledWith('김학생\n학생 ID: 7\n초기 PIN: 5678')
    }
  })
})
