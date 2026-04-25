import { describe, it, expect } from 'vitest'
import { groupByTopic, type TopicStat } from './topicStats'

const detail = (over: Partial<{
  questionNumber: number
  isCorrect: boolean | null | undefined
  topic: string | null
}>) => ({
  questionNumber: over.questionNumber ?? 1,
  isCorrect: over.isCorrect,
  topic: over.topic ?? null,
})

describe('groupByTopic', () => {
  it('groups by topic and counts correct/total', () => {
    const result = groupByTopic([
      detail({ questionNumber: 1, topic: '일차함수', isCorrect: true }),
      detail({ questionNumber: 2, topic: '일차함수', isCorrect: false }),
      detail({ questionNumber: 3, topic: '도형', isCorrect: true }),
    ])
    const map = new Map(result.map(r => [r.topic, r]))
    expect(map.get('일차함수')).toEqual<TopicStat>({
      topic: '일차함수', total: 2, correct: 1, ratio: 0.5,
    })
    expect(map.get('도형')).toEqual<TopicStat>({
      topic: '도형', total: 1, correct: 1, ratio: 1,
    })
  })

  it('groups null/empty topic into "기타"', () => {
    const result = groupByTopic([
      detail({ questionNumber: 1, topic: null, isCorrect: true }),
      detail({ questionNumber: 2, topic: '', isCorrect: false }),
      detail({ questionNumber: 3, topic: '   ', isCorrect: false }),
    ])
    expect(result).toHaveLength(1)
    expect(result[0]).toEqual<TopicStat>({
      topic: '기타', total: 3, correct: 1, ratio: 1 / 3,
    })
  })

  it('ignores ungraded items (isCorrect null) from total', () => {
    const result = groupByTopic([
      detail({ questionNumber: 1, topic: '도형', isCorrect: true }),
      detail({ questionNumber: 2, topic: '도형', isCorrect: null }),
      detail({ questionNumber: 3, topic: '도형', isCorrect: undefined }),
    ])
    expect(result[0]).toEqual<TopicStat>({
      topic: '도형', total: 1, correct: 1, ratio: 1,
    })
  })

  it('places "기타" group at the end of the result', () => {
    const result = groupByTopic([
      detail({ topic: null, isCorrect: true }),
      detail({ topic: '도형', isCorrect: true }),
      detail({ topic: '함수', isCorrect: true }),
    ])
    expect(result.map(r => r.topic)).toEqual(['도형', '함수', '기타'])
  })

  it('returns empty array for empty input', () => {
    expect(groupByTopic([])).toEqual([])
  })

  it('returns ratio 0 when no graded items in a topic', () => {
    const result = groupByTopic([
      detail({ topic: '도형', isCorrect: null }),
    ])
    expect(result).toEqual([])
  })
})
