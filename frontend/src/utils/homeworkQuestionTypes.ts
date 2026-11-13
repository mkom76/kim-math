import catalog from '@/data/homeworkQuestionTypes.json'

interface CatalogNode {
  id: string
  name: string
  aliases?: string[]
  children?: CatalogNode[]
  code?: string
  proposed?: boolean
  corrected?: boolean
}

export interface HomeworkQuestionType {
  readonly id: string
  readonly code: string
  readonly path: readonly string[]
  readonly proposed: boolean
  readonly corrected: boolean
}

export interface HomeworkTypeOption extends Record<string, unknown> {
  value: string
  label: string
  children?: HomeworkTypeOption[]
}

export const homeworkTypeCatalogVersion = catalog.version
const subjects: CatalogNode[] = catalog.subjects
const typeById = new Map<string, HomeworkQuestionType>()

function toOption(node: CatalogNode, parents: string[]): HomeworkTypeOption {
  const path = [...parents, node.name]
  if (node.children) {
    return { value: node.id, label: node.name, children: node.children.map(child => toOption(child, path)) }
  }
  if (!node.code || path.length !== 4 || typeById.has(node.id)) {
    throw new Error('숙제 유형트리의 문항 유형 식별자가 올바르지 않습니다.')
  }
  typeById.set(node.id, Object.freeze({
    id: node.id,
    code: node.code,
    path: Object.freeze(path),
    proposed: node.proposed === true,
    corrected: node.corrected === true,
  }))
  return { value: node.id, label: node.name }
}

export const homeworkTypeOptions: HomeworkTypeOption[] = subjects.map(subject => toOption(subject, []))
export const homeworkQuestionTypes: readonly HomeworkQuestionType[] = Object.freeze([...typeById.values()])

/** A display label, a THM number, or an unknown AI response is never a type ID. */
export function getHomeworkQuestionType(id: string | null | undefined): HomeworkQuestionType | undefined {
  return id ? typeById.get(id) : undefined
}

/** Paths remain arrays: type labels can themselves contain fractions and slashes. */
export function formatHomeworkQuestionType(id: string | null | undefined): string {
  return getHomeworkQuestionType(id)?.path.join(' › ') ?? '미분류'
}

function normalizeSearch(value: string): string {
  return value.toLowerCase()
    .replace(/수학\s*(?:2|ⅱ|ii)(?![a-z0-9])/g, '미적분1')
    .replace(/수학\s*(?:1|ⅰ|i)(?![a-z0-9])/g, '대수')
}

export function matchesHomeworkTypeSearch(pathLabels: readonly string[], keyword: string): boolean {
  const haystack = normalizeSearch(pathLabels.join(' '))
  return normalizeSearch(keyword).trim().split(/\s+/).every(word => haystack.includes(word))
}
