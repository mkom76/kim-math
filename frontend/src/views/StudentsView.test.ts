import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { academyAPI, academyClassAPI, studentAPI, type Student } from '@/api/client'
import StudentsView from './StudentsView.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/client', () => ({
  studentAPI: { getStudents: vi.fn(), updateStudent: vi.fn(), resetPin: vi.fn() },
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
const dialogStub = { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>' }
const buttonStub = { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' }

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
        ElSelect: true, ElOption: true, ElInput: true, ElAvatar: true, ElPagination: true,
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
})
