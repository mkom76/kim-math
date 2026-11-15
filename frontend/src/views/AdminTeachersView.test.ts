import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { adminTeacherAPI } from '@/api/client'
import { ElMessage } from 'element-plus'
import AdminTeachersView from './AdminTeachersView.vue'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userId: 1 }) }))
vi.mock('@/api/client', () => ({ adminTeacherAPI: { list: vi.fn(), updateName: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
const slot = { template: '<div><slot /></div>' }
const teacher = { teacherId: 2, name: '기존 이름', username: 'sample', role: 'ASSISTANT', ownedClassCount: 0 }

async function openEditor() {
  const wrapper = mount(AdminTeachersView, { global: {
    directives: { loading: () => {} },
    stubs: {
      ElCard: slot, ElTable: slot, ElForm: slot, ElFormItem: slot, ElSelect: true, ElOption: true,
      ElTableColumn: { setup: () => ({ teacher }), template: '<div><slot :row="teacher" /></div>' },
      ElButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      ElDialog: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>' },
      ElInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
      ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
    },
  } })
  await flushPromises()
  await wrapper.findAll('button').find(b => b.text() === '이름 수정')!.trigger('click')
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  teacher.name = '기존 이름'
  vi.mocked(adminTeacherAPI.list).mockResolvedValue({ data: [teacher] } as never)
  vi.mocked(adminTeacherAPI.updateName).mockResolvedValue({ data: { teacherId: 2, name: '새 이름' } } as never)
})

it('shows the shared-account warning and saves a trimmed name for the selected account', async () => {
  const w = await openEditor()
  expect(w.text()).toContain('모든 소속 학원')
  expect(w.text()).toContain('sample')
  await w.find('input').setValue('  새 이름  ')
  await w.findAll('button').find(b => b.text() === '저장')!.trigger('click')
  await flushPromises()
  expect(adminTeacherAPI.updateName).toHaveBeenCalledWith(2, '새 이름')
  expect(teacher.name).toBe('새 이름')
  expect(w.find('input').exists()).toBe(false)
})

it('blocks a blank name and cancels without saving', async () => {
  const w = await openEditor()
  await w.find('input').setValue('   ')
  await w.findAll('button').find(b => b.text() === '저장')!.trigger('click')
  expect(adminTeacherAPI.updateName).not.toHaveBeenCalled()
  await w.findAll('button').find(b => b.text() === '취소')!.trigger('click')
  expect(w.find('input').exists()).toBe(false)
  expect(teacher.name).toBe('기존 이름')
})

it('keeps the edited name available after a server failure', async () => {
  vi.mocked(adminTeacherAPI.updateName).mockRejectedValue(new Error('network'))
  const w = await openEditor()
  await w.find('input').setValue('새 이름')
  await w.findAll('button').find(b => b.text() === '저장')!.trigger('click')
  await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('이름 변경에 실패했습니다')
  expect(w.find('input').element.value).toBe('새 이름')
  expect(teacher.name).toBe('기존 이름')
})
