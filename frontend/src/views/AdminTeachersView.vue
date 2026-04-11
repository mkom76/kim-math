<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminTeacherAPI, type AdminTeacherDto, type InviteTeacherRequest } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const teachers = ref<AdminTeacherDto[]>([])
const loading = ref(false)
const inviteDialogVisible = ref(false)
const inviting = ref(false)

const inviteForm = ref<InviteTeacherRequest>({
  username: '',
  name: '',
  tempPin: '',
  role: 'TEACHER',
})

async function loadTeachers() {
  loading.value = true
  try {
    const res = await adminTeacherAPI.list()
    teachers.value = res.data
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '선생님 목록을 불러올 수 없습니다')
  } finally {
    loading.value = false
  }
}

function openInviteDialog() {
  inviteForm.value = { username: '', name: '', tempPin: '', role: 'TEACHER' }
  inviteDialogVisible.value = true
}

async function submitInvite() {
  if (!inviteForm.value.username) {
    ElMessage.error('username을 입력하세요')
    return
  }
  inviting.value = true
  try {
    await adminTeacherAPI.invite(inviteForm.value)
    ElMessage.success('초대되었습니다')
    inviteDialogVisible.value = false
    await loadTeachers()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '초대에 실패했습니다')
  } finally {
    inviting.value = false
  }
}

async function changeRole(teacher: AdminTeacherDto, newRole: 'TEACHER' | 'ACADEMY_ADMIN') {
  if (teacher.role === newRole) return
  try {
    await adminTeacherAPI.updateRole(teacher.teacherId, newRole)
    ElMessage.success('역할이 변경되었습니다')
    await loadTeachers()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '역할 변경 실패')
  }
}

async function removeTeacher(teacher: AdminTeacherDto) {
  if (teacher.teacherId === authStore.userId) {
    ElMessage.warning('본인은 제거할 수 없습니다')
    return
  }

  try {
    await ElMessageBox.confirm(
      `${teacher.name} 선생님을 학원에서 제거하시겠습니까?\n` +
      `소유하고 있는 ${teacher.ownedClassCount}개 반은 자동으로 관리자(본인)에게 이관됩니다.`,
      '선생님 제거 확인',
      {
        confirmButtonText: '제거',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )
  } catch {
    return  // user cancelled
  }

  try {
    await adminTeacherAPI.remove(teacher.teacherId)
    ElMessage.success('제거되었습니다')
    await loadTeachers()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '제거 실패')
  }
}

onMounted(loadTeachers)
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h2 style="margin: 0;">선생님 관리</h2>
      <el-button type="primary" @click="openInviteDialog">선생님 초대</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="teachers" stripe>
        <el-table-column prop="name" label="이름" width="120" />
        <el-table-column prop="username" label="아이디" width="160" />
        <el-table-column label="역할" width="180">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              :disabled="row.teacherId === authStore.userId"
              @change="(v: 'TEACHER' | 'ACADEMY_ADMIN') => changeRole(row, v)"
            >
              <el-option label="선생님" value="TEACHER" />
              <el-option label="관리자" value="ACADEMY_ADMIN" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="ownedClassCount" label="담당 반 수" width="120" align="center" />
        <el-table-column label="작업" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              size="small"
              :disabled="row.teacherId === authStore.userId"
              @click="removeTeacher(row)"
            >
              제거
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="inviteDialogVisible" title="선생님 초대" width="480px">
      <el-form :model="inviteForm" label-position="left" label-width="80px">
        <el-form-item label="아이디" required>
          <el-input v-model="inviteForm.username" placeholder="기존 또는 신규 username" />
        </el-form-item>
        <el-form-item label="이름">
          <el-input v-model="inviteForm.name" placeholder="신규 생성 시에만 필요" />
        </el-form-item>
        <el-form-item label="임시 PIN">
          <el-input v-model="inviteForm.tempPin" placeholder="신규 생성 시에만 필요 (6자리)" maxlength="6" />
        </el-form-item>
        <el-form-item label="역할">
          <el-select v-model="inviteForm.role">
            <el-option label="선생님" value="TEACHER" />
            <el-option label="관리자" value="ACADEMY_ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="inviting" @click="submitInvite">초대</el-button>
      </template>
    </el-dialog>
  </div>
</template>
