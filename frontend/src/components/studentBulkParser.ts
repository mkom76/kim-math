import type { StudentBulkCreateItem } from '@/api/client'

export interface ParsedStudentRow {
  ok: boolean
  data?: StudentBulkCreateItem
  /** Raw input values, populated only when normalization changed them. */
  raw: {
    grade?: string
    school?: string
    parentPhone?: string
    contactPhone?: string
  }
  warnings: string[]
  errors: string[]
}

/** Format a Korean phone number as 3-3-4 or 3-4-4 based on digit count. */
export function normalizePhone(raw: string): { value: string; ok: boolean } {
  const digits = (raw || '').replace(/\D/g, '')
  if (digits.length === 11) {
    return { value: `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`, ok: true }
  }
  if (digits.length === 10) {
    return { value: `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`, ok: true }
  }
  return { value: (raw || '').trim(), ok: false }
}

/** Map common Korean grade variants to canonical "고1" / "중2" / "초3" form. */
export function normalizeGrade(raw: string): { value: string; canonical: boolean } {
  const trimmed = (raw || '').trim()
  if (!trimmed) return { value: '', canonical: false }
  if (/^(고[1-3]|중[1-3]|초[1-6])$/.test(trimmed)) {
    return { value: trimmed, canonical: true }
  }
  const compact = trimmed.replace(/\s+/g, '')
  let m = compact.match(/^(?:고|고등|고등학교|H|h)([1-3])(?:학?년?)?$/)
  if (m) return { value: `고${m[1]}`, canonical: true }
  m = compact.match(/^(?:중|중등|중학|중학교|M|m)([1-3])(?:학?년?)?$/)
  if (m) return { value: `중${m[1]}`, canonical: true }
  m = compact.match(/^(?:초|초등|초등학교|E|e)([1-6])(?:학?년?)?$/)
  if (m) return { value: `초${m[1]}`, canonical: true }
  return { value: trimmed, canonical: false }
}

export function normalizeSchool(raw: string): string {
  return (raw || '').trim().replace(/\s+/g, ' ')
}

export function parseStudentRows(text: string): ParsedStudentRow[] {
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0)
  return lines.map(line => {
    const cols = line.split('\t').map(c => c.trim())
    if (cols.length < 5) {
      return {
        ok: false,
        raw: {},
        warnings: [],
        errors: ['열 부족 — 이름·학년·학교·보호자이름·보호자전화 (학생전화는 선택)'],
      }
    }
    const [name, rawGrade, rawSchool, parentName, rawParentPhone, rawContactPhone = ''] = cols
    const errors: string[] = []
    const warnings: string[] = []

    if (!name) errors.push('이름이 비어있습니다')
    if (!rawGrade) errors.push('학년이 비어있습니다')
    if (!rawSchool) errors.push('학교가 비어있습니다')
    if (!parentName) errors.push('보호자 이름이 비어있습니다')

    const grade = normalizeGrade(rawGrade)
    if (rawGrade && !grade.canonical) {
      warnings.push(`학년을 인식하지 못했습니다 (입력: ${rawGrade}). 그대로 저장됩니다.`)
    }

    const school = normalizeSchool(rawSchool)

    const phone = normalizePhone(rawParentPhone)
    if (!phone.ok) {
      errors.push('보호자 전화 형식 오류 — 숫자 10~11자리 필요 (예: 010-1234-5678)')
    }

    let contactPhone = ''
    if (rawContactPhone) {
      const c = normalizePhone(rawContactPhone)
      if (!c.ok) {
        errors.push('학생 전화 형식 오류 — 숫자 10~11자리 필요 (예: 010-1234-5678)')
      } else {
        contactPhone = c.value
      }
    }

    const raw = {
      grade: rawGrade !== grade.value ? rawGrade : undefined,
      school: rawSchool !== school ? rawSchool : undefined,
      parentPhone: rawParentPhone !== phone.value ? rawParentPhone : undefined,
      contactPhone: rawContactPhone && rawContactPhone !== contactPhone ? rawContactPhone : undefined,
    }

    if (errors.length > 0) return { ok: false, raw, warnings, errors }

    return {
      ok: true,
      raw,
      warnings,
      errors: [],
      data: {
        name,
        grade: grade.value,
        school,
        parentName,
        parentPhone: phone.value,
        contactPhone,
      },
    }
  })
}
