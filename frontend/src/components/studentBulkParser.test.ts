import { describe, it, expect } from 'vitest'
import {
  normalizeGrade,
  normalizePhone,
  normalizeSchool,
  parseStudentRows,
} from './studentBulkParser'

describe('normalizePhone', () => {
  it.each([
    ['010-1234-5678', '010-1234-5678'],
    ['01012345678', '010-1234-5678'],
    ['010 1234 5678', '010-1234-5678'],
    ['010.1234.5678', '010-1234-5678'],
  ])('formats 11-digit input %s → %s', (input, expected) => {
    const r = normalizePhone(input)
    expect(r.ok).toBe(true)
    expect(r.value).toBe(expected)
  })

  it('formats 10-digit input as 3-3-4', () => {
    const r = normalizePhone('0212345678')
    expect(r.ok).toBe(true)
    expect(r.value).toBe('021-234-5678')
  })

  it.each(['abc', '12345', '01012345', ''])('rejects %s', (input) => {
    expect(normalizePhone(input).ok).toBe(false)
  })
})

describe('normalizeGrade', () => {
  it.each([
    ['고1', '고1'],
    ['고2', '고2'],
    ['고등학교 1학년', '고1'],
    ['고등학교1학년', '고1'],
    ['고등 1학년', '고1'],
    ['고 2', '고2'],
    ['고1년', '고1'],
    ['H3', '고3'],
    ['h2', '고2'],
    ['중1', '중1'],
    ['중학교 2학년', '중2'],
    ['초3', '초3'],
    ['초등학교 6학년', '초6'],
  ])('normalizes %s → %s', (input, expected) => {
    const r = normalizeGrade(input)
    expect(r.canonical).toBe(true)
    expect(r.value).toBe(expected)
  })

  it.each(['재수', 'N수', '일반', '대학교 1학년'])('keeps unknown grade %s with canonical=false', (input) => {
    const r = normalizeGrade(input)
    expect(r.canonical).toBe(false)
    expect(r.value).toBe(input)
  })

  it('rejects out-of-range grade numbers', () => {
    expect(normalizeGrade('고4').canonical).toBe(false)
    expect(normalizeGrade('초7').canonical).toBe(false)
  })
})

describe('normalizeSchool', () => {
  it('trims and collapses whitespace', () => {
    expect(normalizeSchool('  A   고등학교  ')).toBe('A 고등학교')
    expect(normalizeSchool('B고\t')).toBe('B고')
  })
})

describe('parseStudentRows', () => {
  it('parses a valid row and normalizes all fields', () => {
    const rows = parseStudentRows('김철수\t고등학교 1학년\tA고\t김보호\t01012345678')
    expect(rows).toHaveLength(1)
    const r = rows[0]!
    expect(r.ok).toBe(true)
    expect(r.errors).toEqual([])
    expect(r.data).toEqual({
      name: '김철수',
      grade: '고1',
      school: 'A고',
      parentName: '김보호',
      parentPhone: '010-1234-5678',
      contactPhone: '',
    })
    // Both grade and phone were transformed → raw should hold the originals
    expect(r.raw.grade).toBe('고등학교 1학년')
    expect(r.raw.parentPhone).toBe('01012345678')
  })

  it('skips raw when input already matches normalized', () => {
    const rows = parseStudentRows('김철수\t고1\tA고\t김보호\t010-1234-5678')
    const r = rows[0]!
    expect(r.ok).toBe(true)
    expect(r.raw.grade).toBeUndefined()
    expect(r.raw.parentPhone).toBeUndefined()
  })

  it('accepts optional contact phone and normalizes it', () => {
    const rows = parseStudentRows('김철수\t고1\tA고\t김보호\t01012345678\t010 9999 8888')
    expect(rows[0]!.data?.contactPhone).toBe('010-9999-8888')
    expect(rows[0]!.raw.contactPhone).toBe('010 9999 8888')
  })

  it('flags missing columns as error', () => {
    const rows = parseStudentRows('김철수\t고1\tA고\t김보호')
    expect(rows[0]!.ok).toBe(false)
    expect(rows[0]!.errors[0]).toMatch(/열 부족/)
  })

  it('flags invalid parent phone as error with example', () => {
    const rows = parseStudentRows('김철수\t고1\tA고\t김보호\tabc')
    expect(rows[0]!.ok).toBe(false)
    expect(rows[0]!.errors.join(' ')).toMatch(/010-1234-5678/)
  })

  it('flags blank middle fields individually', () => {
    // Line-trim strips leading/trailing tabs, so put real values at both ends.
    const rows = parseStudentRows('김철수\t\t\t김보호\t01012345678')
    const errs = rows[0]!.errors.join(' ')
    expect(rows[0]!.ok).toBe(false)
    expect(errs).toMatch(/학년/)
    expect(errs).toMatch(/학교/)
  })

  it('warns on unrecognized grade but still accepts the row', () => {
    const rows = parseStudentRows('김철수\t재수\tA고\t김보호\t01012345678')
    const r = rows[0]!
    expect(r.ok).toBe(true)
    expect(r.warnings.length).toBeGreaterThan(0)
    expect(r.data?.grade).toBe('재수')
  })

  it('skips blank lines', () => {
    const rows = parseStudentRows('\n\n김철수\t고1\tA고\t김보호\t01012345678\n\n')
    expect(rows).toHaveLength(1)
  })

  it('parses multiple rows', () => {
    const text = [
      '김철수\t고1\tA고\t김보호\t01012345678',
      '이영희\t고2\tB고\t이부모\t01087654321',
    ].join('\n')
    const rows = parseStudentRows(text)
    expect(rows).toHaveLength(2)
    expect(rows.every(r => r.ok)).toBe(true)
  })
})
