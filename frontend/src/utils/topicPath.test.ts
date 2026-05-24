import { describe, it, expect } from 'vitest'
import { parseTopicPath, joinTopicLevels, TOPIC_MAX_LEVELS } from './topicPath'

describe('parseTopicPath', () => {
  it.each([
    ['함수/일차함수/그래프', ['함수', '일차함수', '그래프']],
    ['함수 > 일차함수 > 그래프', ['함수', '일차함수', '그래프']],
    ['함수 › 일차함수', ['함수', '일차함수']],
    ['함수 / 일차함수 › 그래프 > 절편', ['함수', '일차함수', '그래프', '절편']],
    ['  함수  /  일차함수  ', ['함수', '일차함수']],
    ['//함수///일차함수///', ['함수', '일차함수']],
    ['일차함수', ['일차함수']],
  ])('parses %j → %j', (input, expected) => {
    expect(parseTopicPath(input)).toEqual(expected)
  })

  it.each([null, undefined, '', '   '])('returns empty for %j', input => {
    expect(parseTopicPath(input)).toEqual([])
  })

  it('caps at TOPIC_MAX_LEVELS', () => {
    expect(parseTopicPath('a/b/c/d/e/f/g')).toHaveLength(TOPIC_MAX_LEVELS)
  })
})

describe('joinTopicLevels', () => {
  it('joins with canonical separator', () => {
    expect(joinTopicLevels(['함수', '일차함수'])).toBe('함수 › 일차함수')
  })

  it('drops blank segments', () => {
    expect(joinTopicLevels(['a', '', '  ', 'b'])).toBe('a › b')
  })

  it('empty input → empty string', () => {
    expect(joinTopicLevels([])).toBe('')
  })
})
