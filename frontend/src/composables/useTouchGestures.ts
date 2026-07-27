import { ref } from 'vue'

interface TouchState {
  startDistance: number
  startX: number
  startY: number
  lastX: number
  lastY: number
}

export function useTouchGestures() {
  const zoom = ref(1)
  const panX = ref(0)
  const panY = ref(0)
  const isDragging = ref(false)
  const touchState = ref<TouchState | null>(null)

  const handleTouchStart = (event: TouchEvent) => {
    if (event.touches.length === 2) {
      // Pinch gesture
      const touch1 = event.touches.item(0)
      const touch2 = event.touches.item(1)
      if (!touch1 || !touch2) return
      const distance = Math.hypot(
        touch2.clientX - touch1.clientX,
        touch2.clientY - touch1.clientY
      )
      touchState.value = {
        startDistance: distance,
        startX: (touch1.clientX + touch2.clientX) / 2,
        startY: (touch1.clientY + touch2.clientY) / 2,
        lastX: (touch1.clientX + touch2.clientX) / 2,
        lastY: (touch1.clientY + touch2.clientY) / 2
      }
    } else if (event.touches.length === 1) {
      // Pan gesture
      isDragging.value = true
      const touch = event.touches.item(0)
      if (!touch) return
      touchState.value = {
        startDistance: 0,
        startX: touch.clientX - panX.value,
        startY: touch.clientY - panY.value,
        lastX: touch.clientX,
        lastY: touch.clientY
      }
    }
  }

  const handleTouchMove = (event: TouchEvent) => {
    event.preventDefault()

    if (event.touches.length === 2 && touchState.value) {
      // Pinch zoom
      const touch1 = event.touches.item(0)
      const touch2 = event.touches.item(1)
      if (!touch1 || !touch2) return
      const distance = Math.hypot(
        touch2.clientX - touch1.clientX,
        touch2.clientY - touch1.clientY
      )
      const scale = distance / touchState.value.startDistance
      const newZoom = Math.max(0.5, Math.min(5, zoom.value * scale))
      zoom.value = newZoom
      touchState.value.startDistance = distance
    } else if (event.touches.length === 1 && isDragging.value && touchState.value) {
      // Pan
      const touch = event.touches.item(0)
      if (!touch) return
      panX.value = touch.clientX - touchState.value.startX
      panY.value = touch.clientY - touchState.value.startY
    }
  }

  const handleTouchEnd = () => {
    isDragging.value = false
    touchState.value = null
  }

  const resetZoomAndPan = () => {
    zoom.value = 1
    panX.value = 0
    panY.value = 0
  }

  return {
    zoom,
    panX,
    panY,
    isDragging,
    handleTouchStart,
    handleTouchMove,
    handleTouchEnd,
    resetZoomAndPan
  }
}
