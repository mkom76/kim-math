import { describe, it, expect } from 'vitest'
import { parseTextbookProblems } from './textbookProblemsParser'

describe('parseTextbookProblems', () => {
  it('parses tab-separated rows (Excel paste)', () => {
    const text = '1\t객관식\t일차함수\t3\thttps://y/1\n2\t주관식\t이차방정식\tx=2'
    const result = parseTextbookProblems(text)
    expect(result).toHaveLength(2)
    expect(result[0]!.ok).toBe(true)
    expect(result[0]!.data).toEqual({
      number: 1, questionType: 'OBJECTIVE', topic: '일차함수',
      answer: '3', videoLink: 'https://y/1',
    })
    expect(result[1]!.data).toEqual({
      number: 2, questionType: 'SUBJECTIVE', topic: '이차방정식',
      answer: 'x=2', videoLink: null,
    })
  })

  it('parses comma-separated rows', () => {
    const text = '1,객관식,일차함수,3,https://y/1\n2,서술형,도형'
    const result = parseTextbookProblems(text)
    expect(result).toHaveLength(2)
    expect(result[0]!.data!.questionType).toBe('OBJECTIVE')
    expect(result[1]!.data!.questionType).toBe('ESSAY')
    expect(result[1]!.data!.topic).toBe('도형')
    expect(result[1]!.data!.answer).toBeNull()
    expect(result[1]!.data!.videoLink).toBeNull()
  })

  it('treats empty/whitespace cells as null', () => {
    const text = '1\t\t\t\t'
    const result = parseTextbookProblems(text)
    expect(result[0]!.data).toEqual({
      number: 1, questionType: null, topic: null,
      answer: null, videoLink: null,
    })
  })

  it('accepts English aliases case-insensitively', () => {
    const text = '1\tobjective\n2\t SUBJECTIVE \n3\tEssay'
    const result = parseTextbookProblems(text)
    expect(result[0]!.data!.questionType).toBe('OBJECTIVE')
    expect(result[1]!.data!.questionType).toBe('SUBJECTIVE')
    expect(result[2]!.data!.questionType).toBe('ESSAY')
  })

  it('skips empty lines', () => {
    const text = '1\t객관식\n\n\n2\t주관식\n   \n'
    const result = parseTextbookProblems(text)
    expect(result).toHaveLength(2)
    expect(result[0]!.data!.number).toBe(1)
    expect(result[1]!.data!.number).toBe(2)
  })

  it('flags row with non-numeric or missing number as error', () => {
    const text = 'abc\t객관식\n\t주관식\n0\t주관식'
    const result = parseTextbookProblems(text)
    expect(result[0]!.ok).toBe(false)
    expect(result[0]!.errors[0]).toContain('번호')
    expect(result[1]!.ok).toBe(false)
    expect(result[2]!.ok).toBe(false) // 0 not allowed
  })

  it('flags unknown questionType as error but keeps number/other fields', () => {
    const text = '1\t어쩌구\t주제'
    const result = parseTextbookProblems(text)
    expect(result[0]!.ok).toBe(false)
    expect(result[0]!.errors[0]).toContain('형식')
    expect(result[0]!.data!.number).toBe(1)
    expect(result[0]!.data!.topic).toBe('주제')
  })

  it('detects tab separator when both tab and comma present', () => {
    // Tab takes priority because it's how Excel pastes; commas may appear in answer
    const text = '1\t주관식\t주제\tx, y'
    const result = parseTextbookProblems(text)
    expect(result[0]!.data!.answer).toBe('x, y')
  })

  it('returns empty array for empty input', () => {
    expect(parseTextbookProblems('')).toEqual([])
    expect(parseTextbookProblems('   \n  \n')).toEqual([])
  })
})
