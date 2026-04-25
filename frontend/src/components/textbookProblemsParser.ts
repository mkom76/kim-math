import type { TextbookQuestionType } from '@/api/client'

export interface ParsedRowData {
  number: number | null
  questionType: TextbookQuestionType | null
  topic: string | null
  answer: string | null
  videoLink: string | null
}

export interface ParsedRow {
  /** 1-indexed line number in the source text (skips empty lines counted) */
  lineNumber: number
  raw: string
  ok: boolean
  errors: string[]
  data: ParsedRowData | null
}

const TYPE_MAP: Record<string, TextbookQuestionType> = {
  객관식: 'OBJECTIVE',
  objective: 'OBJECTIVE',
  주관식: 'SUBJECTIVE',
  subjective: 'SUBJECTIVE',
  서술형: 'ESSAY',
  essay: 'ESSAY',
}

const detectSeparator = (line: string): '\t' | ',' => (line.includes('\t') ? '\t' : ',')

const cellOrNull = (raw: string | undefined): string | null => {
  if (raw == null) return null
  const trimmed = raw.trim()
  return trimmed === '' ? null : trimmed
}

const parseTypeCell = (raw: string | null): { value: TextbookQuestionType | null; error: string | null } => {
  if (raw == null) return { value: null, error: null }
  const key = raw.toLowerCase()
  if (key in TYPE_MAP) return { value: TYPE_MAP[key]!, error: null }
  return { value: null, error: `알 수 없는 형식: "${raw}" (객관식/주관식/서술형 또는 OBJECTIVE/SUBJECTIVE/ESSAY)` }
}

export function parseTextbookProblems(text: string): ParsedRow[] {
  if (!text || !text.trim()) return []

  const lines = text.split(/\r?\n/)
  const rows: ParsedRow[] = []

  lines.forEach((line, idx) => {
    if (!line.trim()) return

    const sep = detectSeparator(line)
    const cells = line.split(sep)
    const errors: string[] = []
    const data: ParsedRowData = {
      number: null,
      questionType: null,
      topic: null,
      answer: null,
      videoLink: null,
    }

    // 1: number
    const numberCell = cellOrNull(cells[0])
    if (numberCell == null) {
      errors.push('번호가 비어 있습니다')
    } else {
      const n = Number(numberCell)
      if (!Number.isInteger(n) || n < 1) {
        errors.push(`번호가 올바르지 않습니다: "${numberCell}"`)
      } else {
        data.number = n
      }
    }

    // 2: questionType
    const typeRaw = cellOrNull(cells[1])
    const typeResult = parseTypeCell(typeRaw)
    if (typeResult.error) errors.push(typeResult.error)
    else data.questionType = typeResult.value

    // 3: topic
    data.topic = cellOrNull(cells[2])

    // 4: answer
    data.answer = cellOrNull(cells[3])

    // 5: videoLink
    data.videoLink = cellOrNull(cells[4])

    rows.push({
      lineNumber: idx + 1,
      raw: line,
      ok: errors.length === 0,
      errors,
      data,
    })
  })

  return rows
}
