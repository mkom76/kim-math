import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import { studentAccountMergeAPI, type StudentAccountMergeCandidate } from '@/api/client'
import StudentAccountMergeDialog from './StudentAccountMergeDialog.vue'

vi.mock('@/api/client', () => ({
  studentAccountMergeAPI: {
    getCandidates: vi.fn(),
    preview: vi.fn(),
    merge: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

const group: StudentAccountMergeCandidate = {
  matchReasons: ['학생명·보호자 전화번호 일치'],
  students: [
    {
      id: 11, name: '김학생', grade: '고1', school: '가고', parentName: '보호자',
      parentPhoneMasked: '****-1234', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00',
      enrollments: [{ classId: 1, className: '기존반', status: 'COMPLETED' }],
    },
    {
      id: 22, name: '김학생', grade: '고1', school: '가고', parentName: '보호자',
      parentPhoneMasked: '****-1234', status: 'ACTIVE', createdAt: '2026-08-01T00:00:00',
      enrollments: [{ classId: 2, className: '현재반', status: 'ACTIVE' }],
    },
  ],
}

const dialogStub = {
  props: ['modelValue', 'title'],
  template: '<section v-if="modelValue"><h1>{{ title }}</h1><slot /><slot name="footer" /></section>',
}
const buttonStub = {
  props: ['disabled', 'loading'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
}
const tableStub = { template: '<div><slot /></div>' }

async function mountDialog() {
  const wrapper = mount(StudentAccountMergeDialog, {
    props: { visible: true },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElDialog: dialogStub,
        ElButton: buttonStub,
        ElAlert: { template: '<div><slot /></div>' },
        ElCard: { template: '<div><slot /></div>' },
        ElTag: { template: '<span><slot /></span>' },
        ElEmpty: true,
        ElTable: tableStub,
        ElTableColumn: true,
        ElDescriptions: tableStub,
        ElDescriptionsItem: tableStub,
        ElRadio: true,
        ElCheckbox: true,
        ElInput: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('StudentAccountMergeDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(studentAccountMergeAPI.getCandidates).mockResolvedValue({ data: [group] } as never)
  })

  it('lists only server-provided candidates and opens explicit account selection', async () => {
    const wrapper = await mountDialog()
    expect(studentAccountMergeAPI.getCandidates).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('김학생 #11, 김학생 #22')
    expect(wrapper.text()).toContain('학생명·보호자 전화번호 일치')

    const review = wrapper.findAll('button').find(button => button.text() === '통합 검토')
    await review!.trigger('click')
    expect(wrapper.text()).toContain('대표 계정의 이름·연락처·로그인 정보가 유지됩니다.')
    expect(studentAccountMergeAPI.preview).not.toHaveBeenCalled()
    expect(studentAccountMergeAPI.merge).not.toHaveBeenCalled()
  })

  it('surfaces candidate loading errors', async () => {
    vi.mocked(studentAccountMergeAPI.getCandidates).mockRejectedValue({
      response: { data: { message: '후보 조회 실패' } },
    })
    await mountDialog()
    expect(ElMessage.error).toHaveBeenCalledWith('후보 조회 실패')
  })
})
