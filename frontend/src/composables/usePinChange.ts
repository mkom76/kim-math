import axios from 'axios'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authAPI } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

export function usePinChange() {
  const authStore = useAuthStore()
  const loading = ref(false)
  const pinForm = ref({
    currentPin: '',
    newPin: '',
    confirmPin: '',
  })
  const pinLength = computed(() => authStore.role === 'TEACHER' ? 6 : 4)

  async function handleChangePIN() {
    if (!pinForm.value.currentPin || !pinForm.value.newPin || !pinForm.value.confirmPin) {
      ElMessage.error('모든 필드를 입력해주세요')
      return
    }

    if (pinForm.value.newPin !== pinForm.value.confirmPin) {
      ElMessage.error('새 PIN이 일치하지 않습니다')
      return
    }

    const pinPattern = new RegExp(`^\\d{${pinLength.value}}$`)
    if (!pinPattern.test(pinForm.value.newPin)) {
      ElMessage.error(`PIN은 숫자 ${pinLength.value}자리여야 합니다`)
      return
    }

    loading.value = true
    try {
      const response = await authAPI.changePin(pinForm.value.currentPin, pinForm.value.newPin)
      ElMessage.success(response.data.message || 'PIN이 성공적으로 변경되었습니다')
      pinForm.value = { currentPin: '', newPin: '', confirmPin: '' }
    } catch (error: unknown) {
      const message = axios.isAxiosError(error)
        ? error.response?.data?.message || 'PIN 변경에 실패했습니다'
        : 'PIN 변경에 실패했습니다'
      ElMessage.error(message)
    } finally {
      loading.value = false
    }
  }

  return { loading, pinForm, pinLength, handleChangePIN }
}
