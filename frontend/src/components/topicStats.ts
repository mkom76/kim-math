export interface TopicStat {
  topic: string
  total: number
  correct: number
  ratio: number
}

interface MinimalDetail {
  topic?: string | null
  isCorrect?: boolean | null
}

const OTHER = '기타'

const normalizeTopic = (raw?: string | null): string => {
  if (raw == null) return OTHER
  const trimmed = raw.trim()
  return trimmed === '' ? OTHER : trimmed
}

export function groupByTopic(details: MinimalDetail[]): TopicStat[] {
  const order: string[] = []
  const buckets = new Map<string, { total: number; correct: number }>()

  for (const d of details) {
    if (d.isCorrect == null) continue
    const t = normalizeTopic(d.topic)
    if (!buckets.has(t)) {
      buckets.set(t, { total: 0, correct: 0 })
      order.push(t)
    }
    const b = buckets.get(t)!
    b.total += 1
    if (d.isCorrect) b.correct += 1
  }

  const named = order
    .filter(t => t !== OTHER)
    .map(t => toStat(t, buckets.get(t)!))
  const other = buckets.has(OTHER) ? [toStat(OTHER, buckets.get(OTHER)!)] : []
  return [...named, ...other]
}

const toStat = (topic: string, b: { total: number; correct: number }): TopicStat => ({
  topic,
  total: b.total,
  correct: b.correct,
  ratio: b.total === 0 ? 0 : b.correct / b.total,
})
