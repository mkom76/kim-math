export interface TimerAnnouncement {
  id: number
  minutesBefore: number
  message: string
}

export function formatTimer(seconds: number): string {
  const safeSeconds = Math.max(0, Math.ceil(seconds))
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const secs = safeSeconds % 60
  const twoDigits = (value: number) => String(value).padStart(2, '0')

  return hours > 0
    ? `${twoDigits(hours)}:${twoDigits(minutes)}:${twoDigits(secs)}`
    : `${twoDigits(minutes)}:${twoDigits(secs)}`
}

export function dueAnnouncements(
  previousSeconds: number,
  remainingSeconds: number,
  announcements: TimerAnnouncement[],
): TimerAnnouncement[] {
  return announcements
    .filter(({ minutesBefore, message }) => {
      const threshold = minutesBefore * 60
      return message.trim().length > 0 && previousSeconds > threshold && remainingSeconds <= threshold
    })
    .sort((a, b) => b.minutesBefore - a.minutesBefore)
}
