import type { ErrorCauseCounts, ErrorCauses, Submission } from '@/api/client'

// 시험별 집계는 엑셀에서 사용하던 다섯 원인으로 시작한다.
export const ERROR_CAUSES = [
  { key: 'calculationMistake', label: '계산 실수', color: '#2563eb' },
  { key: 'conceptGap', label: '개념 부족', color: '#b45309' },
  { key: 'conditionMisread', label: '조건 해석 실패', color: '#047857' },
  { key: 'ideaGap', label: '발상 부족', color: '#9333ea' },
  { key: 'timeManagement', label: '시간 관리 부족', color: '#be123c' },
] as const

export type ErrorCauseKey = keyof ErrorCauseCounts

export function errorCauseStatus(value?: ErrorCauses | null) {
  return {
    NOT_ENTERED: '미입력',
    PARTIAL: '일부 입력',
    COMPLETE: '입력 완료',
    NEEDS_REVIEW: '재확인 필요',
  }[value?.status ?? 'NOT_ENTERED']
}

export function errorCauseDate(item: Submission) {
  return item.lessonDate?.slice(0, 10) || item.testCreatedAt?.slice(0, 10) || ''
}

export function sortErrorCauseRecords(records: Submission[]) {
  return [...records].sort(
    (a, b) =>
      errorCauseDate(a).localeCompare(errorCauseDate(b)) || (a.testId ?? 0) - (b.testId ?? 0),
  )
}

export function errorCauseDelta(records: Submission[], index: number, key: ErrorCauseKey) {
  if (!records[index] || !records[index - 1]) return null
  const current = records[index]?.errorCauses
  const previous = records[index - 1]?.errorCauses
  if (current?.status === 'NEEDS_REVIEW' || previous?.status === 'NEEDS_REVIEW') return null
  return (current?.[key] ?? 0) - (previous?.[key] ?? 0)
}

export function errorCauseSegments(records: Submission[], key: ErrorCauseKey) {
  const segments: Array<Array<{ index: number; value: number }>> = []
  let segment: Array<{ index: number; value: number }> = []
  for (const [index, record] of records.entries()) {
    const value = record.errorCauses?.[key] ?? 0
    if (record.errorCauses?.status === 'NEEDS_REVIEW') {
      if (segment.length) segments.push(segment)
      segment = []
    } else {
      segment.push({ index, value })
    }
  }
  if (segment.length) segments.push(segment)
  return segments
}
