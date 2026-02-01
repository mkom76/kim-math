import { ref, watch } from 'vue'
import type { Ref } from 'vue'

interface PaginationOptions {
  defaultPage?: number
  defaultPageSize?: number
}

interface PaginationState {
  currentPage: number
  pageSize: number
}

export function usePagination(
  storageKey: string,
  options: PaginationOptions = {}
): {
  currentPage: Ref<number>
  pageSize: Ref<number>
} {
  const { defaultPage = 1, defaultPageSize = 10 } = options

  // localStorage에서 저장된 값 불러오기
  const getStoredState = (): PaginationState => {
    try {
      const stored = localStorage.getItem(`pagination:${storageKey}`)
      if (stored) {
        return JSON.parse(stored)
      }
    } catch (error) {
      console.warn('Failed to load pagination state from localStorage:', error)
    }
    return { currentPage: defaultPage, pageSize: defaultPageSize }
  }

  // localStorage에 상태 저장하기
  const saveState = (state: PaginationState): void => {
    try {
      localStorage.setItem(`pagination:${storageKey}`, JSON.stringify(state))
    } catch (error) {
      console.warn('Failed to save pagination state to localStorage:', error)
    }
  }

  // 초기 상태 로드
  const storedState = getStoredState()
  const currentPage = ref(storedState.currentPage)
  const pageSize = ref(storedState.pageSize)

  // 상태 변경 감지 및 자동 저장
  watch([currentPage, pageSize], () => {
    saveState({
      currentPage: currentPage.value,
      pageSize: pageSize.value
    })
  })

  return {
    currentPage,
    pageSize
  }
}
