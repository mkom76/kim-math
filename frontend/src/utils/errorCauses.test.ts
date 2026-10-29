import { describe, expect, it } from 'vitest'
import type { Submission, ErrorCauses } from '@/api/client'
import {
  errorCauseDelta,
  errorCauseSegments,
  errorCauseStatus,
  sortErrorCauseRecords,
} from './errorCauses'

function record(
  id: number,
  value: number | null,
  status: ErrorCauses['status'] = 'PARTIAL',
): Submission {
  return {
    id,
    testId: id,
    totalScore: 80,
    lessonDate: `2026-10-${10 + id}`,
    errorCauses: {
      calculationMistake: value,
      conceptGap: null,
      conditionMisread: null,
      ideaGap: null,
      timeManagement: null,
      status,
    },
  }
}

describe('error cause history', () => {
  it('orders by test dates instead of answer correction time, with a stable tie breaker', () => {
    const first = { ...record(1, 5), submittedAt: '2026-10-20T20:00:00' }
    const second = { ...record(2, 2), submittedAt: '2026-10-12T20:00:00' }
    const third = { ...record(3, 0), lessonDate: null, testCreatedAt: '2026-10-13T20:00:00' }
    expect(sortErrorCauseRecords([third, second, first]).map((item) => item.testId)).toEqual([
      1, 2, 3,
    ])
    expect(
      sortErrorCauseRecords([{ ...second, lessonDate: first.lessonDate }, first]).map(
        (item) => item.testId,
      ),
    ).toEqual([1, 2])
  })

  it('treats missing counts as zero in both chart lines and comparisons', () => {
    expect(errorCauseDelta([record(1, 2), record(2, 0)], 1, 'calculationMistake')).toBe(-2)
    const records = [record(1, 5), record(2, null, 'NOT_ENTERED'), record(3, 0)]
    expect(errorCauseDelta(records, 1, 'calculationMistake')).toBe(-5)
    expect(errorCauseDelta(records, 2, 'calculationMistake')).toBe(0)
    expect(errorCauseSegments(records, 'calculationMistake')).toEqual([
      [{ index: 0, value: 5 }, { index: 1, value: 0 }, { index: 2, value: 0 }],
    ])
  })

  it('handles an absent error-causes object as zero without inventing a first comparison', () => {
    const missing = { ...record(1, null), errorCauses: undefined }
    expect(errorCauseSegments([missing], 'conceptGap')).toEqual([[{ index: 0, value: 0 }]])
    expect(errorCauseDelta([missing], 0, 'conceptGap')).toBeNull()
    expect(errorCauseDelta([missing, record(2, 3)], 1, 'calculationMistake')).toBe(3)
  })

  it('excludes stale records from chart lines and both adjacent comparisons', () => {
    const records = [record(1, 5), record(2, 2, 'NEEDS_REVIEW'), record(3, 0)]
    expect(errorCauseDelta(records, 1, 'calculationMistake')).toBeNull()
    expect(errorCauseDelta(records, 2, 'calculationMistake')).toBeNull()
    expect(errorCauseSegments(records, 'calculationMistake')).toHaveLength(2)
  })

  it('keeps a single zero point and distinguishes never entered from entered zero', () => {
    expect(errorCauseSegments([record(1, 0)], 'calculationMistake')).toEqual([
      [{ index: 0, value: 0 }],
    ])
    expect(errorCauseDelta([record(1, 0)], 0, 'calculationMistake')).toBeNull()
    expect(errorCauseStatus()).toBe('미입력')
    expect(errorCauseStatus(record(1, 0).errorCauses)).toBe('일부 입력')
  })
})
