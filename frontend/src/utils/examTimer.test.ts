import { describe, expect, it } from 'vitest'
import { dueAnnouncements, formatTimer, type TimerAnnouncement } from './examTimer'

describe('formatTimer', () => {
  it('formats minutes and seconds with fixed-width digits', () => {
    expect(formatTimer(905)).toBe('15:05')
    expect(formatTimer(0)).toBe('00:00')
  })

  it('shows hours for long exams', () => {
    expect(formatTimer(3723)).toBe('01:02:03')
  })
})

describe('dueAnnouncements', () => {
  const announcements: TimerAnnouncement[] = [
    { id: 1, minutesBefore: 15, message: '서술형 답안을 작성해주세요.' },
    { id: 2, minutesBefore: 5, message: '답안을 확인해주세요.' },
  ]

  it('returns an announcement only when its threshold is crossed', () => {
    expect(dueAnnouncements(901, 900, announcements)).toEqual([announcements[0]])
    expect(dueAnnouncements(900, 899, announcements)).toEqual([])
  })

  it('handles a delayed browser tick without missing announcements', () => {
    expect(dueAnnouncements(901, 299, announcements)).toEqual(announcements)
  })
})
