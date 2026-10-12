import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { academyAPI, academyClassAPI, studentAPI, type Student } from '@/api/client'
import StudentsView from './StudentsView.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/client', () => ({
  studentAPI: { getStudents: vi.fn(), createStudent: vi.fn(), updateStudent: vi.fn(), resetPin: vi.fn() },
  studentAccountMergeAPI: { getCandidates: vi.fn(), preview: vi.fn(), merge: vi.fn() },
  academyAPI: { getAcademies: vi.fn() },
  academyClassAPI: { getAcademyClasses: vi.fn() },
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))

const student: Student = {
  id: 3, name: '김학생', grade: '고1', school: '고등학교', academyId: 1,
  classId: 11, className: '기본반',
  enrollments: [
    { classId: 11, className: '기본반', status: 'ACTIVE', canManage: true },
    { classId: 12, className: '심화반', status: 'ACTIVE', canManage: true },
  ],
}
const slotStub = { template: '<div><slot /></div>' }
const dialogStub = { props: ['modelValue', 'title'], template: '<section v-if="modelValue" :aria-label="title"><slot /><slot name="footer" /></section>' }
const buttonStub = { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' }
const inputStub = {
  props: ['modelValue', 'placeholder', 'readonly'], emits: ['update:modelValue'],
  template: '<div><input :value="modelValue" :placeholder="placeholder" :readonly="readonly" @input="$emit(\'update:modelValue\', $event.target.value)" /><slot name="append" /></div>',
}
const selectStub = {
  props: ['modelValue'], emits: ['update:modelValue'],
  template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
}

async function mountView(role: 'TEACHER' | 'ACADEMY_ADMIN' | 'ASSISTANT' = 'TEACHER') {
  const pinia = createPinia()
  const authStore = useAuthStore(pinia)
  authStore.role = 'TEACHER'
  authStore.activeRole = role
  authStore.activeAcademyId = 1
  const wrapper = mount(StudentsView, {
    global: {
      plugins: [pinia],
      directives: { loading: () => {} },
      stubs: {
        StudentBulkImportDialog: true,
        StudentEnrollmentDialog: true,
        StudentAccountMergeDialog: true,
        ElCard: slotStub, ElRow: slotStub, ElCol: slotStub, ElIcon: slotStub, ElTag: slotStub,
        ElTable: slotStub,
        ElTableColumn: { setup: () => ({ student }), template: '<div><slot :row="student" /></div>' },
        ElForm: slotStub, ElFormItem: slotStub,
        ElDialog: dialogStub, ElButton: buttonStub,
        ElSelect: selectStub,
        ElOption: { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
        ElInput: inputStub, ElAlert: slotStub, ElAvatar: true, ElPagination: true,
        School: true, OfficeBuilding: true, Edit: true, Delete: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('StudentsView enrollment management', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(studentAPI.getStudents).mockResolvedValue({ data: { content: [student] } } as never)
    vi.mocked(studentAPI.updateStudent).mockResolvedValue({ data: student } as never)
    vi.mocked(academyAPI.getAcademies).mockResolvedValue({ data: [{ id: 1, name: '학원' }] } as never)
    vi.mocked(academyClassAPI.getAcademyClasses).mockResolvedValue({ data: [{ id: 11, name: '기본반', academyId: 1 }] } as never)
  })

  it('shows all ongoing memberships and edits basic information without a class assignment', async () => {
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('기본반')
    expect(wrapper.text()).toContain('심화반')
    await wrapper.get('[data-test="edit-student"]').trigger('click')
    await wrapper.get('[data-test="submit-student"]').trigger('click')
    await flushPromises()

    expect(studentAPI.updateStudent).toHaveBeenCalledExactlyOnceWith(3, {
      name: '김학생', grade: '고1', school: '고등학교', academyId: 1,
      parentName: undefined, parentPhone: undefined, contactPhone: undefined,
    })
    expect(studentAPI.updateStudent).not.toHaveBeenCalledWith(3, expect.objectContaining({ classId: expect.anything() }))
    expect(studentAPI.getStudents).toHaveBeenCalledTimes(2)
  })

  it('limits full-account deletion to academy admins', async () => {
    const teacher = await mountView()
    const admin = await mountView('ACADEMY_ADMIN')
    expect(teacher.find('[data-test="delete-student"]').exists()).toBe(false)
    expect(admin.find('[data-test="delete-student"]').exists()).toBe(true)
  })

  it('shows a read-only membership action and hides basic edits for assistants', async () => {
    const wrapper = await mountView('ASSISTANT')
    expect(wrapper.text()).toContain('소속 보기')
    expect(wrapper.find('[data-test="edit-student"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="delete-student"]').exists()).toBe(false)
  })

  it.each([null, 'parent-token'])('shows registration details for consent token %s', async (consentToken) => {
    vi.mocked(studentAPI.createStudent).mockResolvedValue({ data: {
      ...student, id: 4, parentName: '보호자', parentPhone: '010-1234-5678', consentToken,
    } } as never)
    const wrapper = await mountView()
    await wrapper.findAll('button').find(button => button.text() === '학생 추가')!.trigger('click')
    await wrapper.get('input[placeholder="학생명을 입력하세요"]').setValue('김학생')
    await wrapper.get('input[placeholder="학년을 입력하세요 (예: 중1, 고2)"]').setValue('고1')
    await wrapper.get('input[placeholder="학교명을 입력하세요"]').setValue('고등학교')
    await wrapper.get('input[placeholder="보호자 이름을 입력하세요"]').setValue('보호자')
    await wrapper.get('input[placeholder="010-1234-5678"]').setValue('010-1234-5678')
    await wrapper.findAll('select').at(-1)!.setValue('11')
    await wrapper.get('[data-test="submit-student"]').trigger('click')
    await flushPromises()

    expect(studentAPI.createStudent).toHaveBeenCalledOnce()
    if (consentToken) {
      expect(wrapper.get('section[aria-label="보호자 동의 링크"]').text()).toContain('아래 링크를 보호자에게 전달하세요')
      expect(wrapper.get('input[readonly]').element).toHaveProperty('value', `${window.location.origin}/consent/parent-token`)
    } else {
      const result = wrapper.get('section[aria-label="학생 등록 완료"]')
      expect(result.text()).toContain('바로 로그인할 수 있습니다')
      expect(result.text()).toContain('학생 ID: 4')
      expect(result.text()).toContain('초기 PIN: 5678')
      expect(result.find('input[readonly]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('/consent/null')
    }
  })
})
