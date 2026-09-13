import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { studentAPI, type AcademyClass, type StudentEnrollment } from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import StudentEnrollmentDialog from './StudentEnrollmentDialog.vue'

vi.mock('@/api/client', () => ({
  studentAPI: {
    getEnrollments: vi.fn(),
    addEnrollment: vi.fn(),
    completeEnrollment: vi.fn(),
    transferEnrollment: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

const current: StudentEnrollment = { classId: 11, className: '기본반', status: 'ACTIVE', canManage: true }
const other: StudentEnrollment = { classId: 12, className: '심화반', status: 'ACTIVE', canManage: false }
const completed: StudentEnrollment = { classId: 13, className: '지난 반', status: 'COMPLETED', canManage: false }
const classes: AcademyClass[] = [
  { id: 11, name: '기본반', academyId: 1 },
  { id: 12, name: '심화반', academyId: 1 },
  { id: 13, name: '지난 반', academyId: 1 },
  { id: 14, name: '새 반', academyId: 1 },
  { id: 15, name: '종강반', academyId: 1, ended: true },
  { id: 16, name: '다른 학원 반', academyId: 2 },
  { id: 17, name: '종료일 있는 반', academyId: 1, endedAt: '2026-09-01T00:00:00' },
  { id: 18, name: '예정 반', academyId: 1 },
]

const dialogStub = {
  props: ['modelValue', 'title'],
  template: '<section v-if="modelValue"><h1>{{ title }}</h1><slot /><slot name="footer" /></section>',
}
const buttonStub = {
  props: ['disabled', 'loading'],
  template: '<button :disabled="disabled || loading"><slot /></button>',
}
const selectStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<select :value="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', Number($event.target.value))"><option value=""/><slot /></select>',
}

async function mountDialog(readOnly = false) {
  const wrapper = mount(StudentEnrollmentDialog, {
    props: {
      visible: true,
      student: { id: 3, name: '김학생', grade: '고1', school: '고등학교', academyId: 1 },
      classes,
      readOnly,
    },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElDialog: dialogStub,
        ElButton: buttonStub,
        ElSelect: selectStub,
        ElOption: { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
        ElTag: { template: '<span><slot /></span>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('StudentEnrollmentDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(studentAPI.getEnrollments).mockResolvedValue({ data: [current, other, completed] } as never)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
  })

  it('excludes current, scheduled, ended and other-academy classes but allows reenrollment', async () => {
    vi.mocked(studentAPI.getEnrollments).mockResolvedValue({
      data: [current, other, completed, { classId: 18, className: '예정 반', status: 'SCHEDULED', canManage: false }],
    } as never)
    const wrapper = await mountDialog()

    expect(wrapper.findAll('option').map(option => option.attributes('value'))).toEqual(['', '13', '14'])
    expect(wrapper.find('[data-test="complete-11"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="complete-12"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('지난 수강 1개')
  })

  it('adds a class once while retaining existing memberships and refreshes the parent', async () => {
    let resolveAdd!: (value: any) => void
    vi.mocked(studentAPI.addEnrollment).mockImplementation(() => new Promise(resolve => { resolveAdd = resolve }))
    const wrapper = await mountDialog()
    await wrapper.get('select').setValue('14')
    await wrapper.get('[data-test="submit-enrollment"]').trigger('click')
    await wrapper.get('[data-test="submit-enrollment"]').trigger('click')

    expect(studentAPI.addEnrollment).toHaveBeenCalledExactlyOnceWith(3, 14)
    expect(wrapper.get('[data-test="complete-11"]').attributes('disabled')).toBeDefined()
    resolveAdd({ data: [current, other, completed, { classId: 14, className: '새 반', status: 'ACTIVE', canManage: true }] })
    await flushPromises()

    expect(wrapper.emitted('updated')).toEqual([[]])
    expect(wrapper.findAll('option').map(option => option.attributes('value'))).toEqual(['', '13', '18'])
    expect(wrapper.text()).toContain('기본반')
    expect(wrapper.text()).toContain('새 반')
  })

  it('does not end enrollment when confirmation is canceled', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    const wrapper = await mountDialog()
    await wrapper.get('[data-test="complete-11"]').trigger('click')
    await flushPromises()

    expect(studentAPI.completeEnrollment).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')).toBeUndefined()
  })

  it('ends only the selected enrollment and moves it into history', async () => {
    vi.mocked(studentAPI.completeEnrollment).mockResolvedValue({
      data: [{ ...current, status: 'COMPLETED', canManage: false }, other, completed],
    } as never)
    const wrapper = await mountDialog()
    await wrapper.get('[data-test="complete-11"]').trigger('click')
    await flushPromises()

    expect(studentAPI.completeEnrollment).toHaveBeenCalledExactlyOnceWith(3, 11)
    expect(wrapper.find('[data-test="complete-11"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('지난 수강 2개')
    expect(wrapper.emitted('updated')).toEqual([[]])
  })

  it('confirms the source and target before transferring through the atomic API', async () => {
    vi.mocked(studentAPI.transferEnrollment).mockResolvedValue({
      data: [{ ...current, status: 'COMPLETED', canManage: false }, other, completed, { classId: 14, className: '새 반', status: 'ACTIVE', canManage: true }],
    } as never)
    const wrapper = await mountDialog()
    await wrapper.get('[data-test="transfer-11"]').trigger('click')
    await wrapper.get('select').setValue('14')
    expect(studentAPI.transferEnrollment).not.toHaveBeenCalled()
    await wrapper.get('[data-test="submit-enrollment"]').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(expect.stringContaining('기본반 수강을 종료하고 새 반'), '반 이동', expect.any(Object))
    expect(studentAPI.transferEnrollment).toHaveBeenCalledExactlyOnceWith(3, 11, 14)
    expect(studentAPI.addEnrollment).not.toHaveBeenCalled()
    expect(studentAPI.completeEnrollment).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')).toEqual([[]])
  })

  it('retains the current assignment and shows server errors when a transfer fails', async () => {
    vi.mocked(studentAPI.transferEnrollment).mockRejectedValue({ response: { data: { message: '종강된 반입니다.' } } })
    const wrapper = await mountDialog()
    await wrapper.get('[data-test="transfer-11"]').trigger('click')
    await wrapper.get('select').setValue('14')
    await wrapper.get('[data-test="submit-enrollment"]').trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('종강된 반입니다.')
    expect(wrapper.find('[data-test="complete-11"]').exists()).toBe(true)
    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.get('[data-test="submit-enrollment"]').attributes('disabled')).toBeUndefined()
  })

  it('blocks mutations when loading fails and lets the user retry', async () => {
    vi.mocked(studentAPI.getEnrollments).mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountDialog()
    expect(wrapper.get('[role="alert"]').text()).toContain('불러오지 못했습니다')
    expect(wrapper.find('select').exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(studentAPI.getEnrollments).toHaveBeenCalledTimes(2)
    expect(wrapper.find('select').exists()).toBe(true)
  })

  it('shows memberships without mutation controls to assistants', async () => {
    const wrapper = await mountDialog(true)
    expect(wrapper.text()).toContain('김학생 · 소속 반')
    expect(wrapper.text()).toContain('기본반')
    expect(wrapper.find('[data-test="complete-11"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="transfer-11"]').exists()).toBe(false)
    expect(wrapper.find('select').exists()).toBe(false)
  })
})
