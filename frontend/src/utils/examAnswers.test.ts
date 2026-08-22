import { describe, expect, it } from 'vitest'
import { parseMultipleAnswer, serializeMultipleAnswer } from './examAnswers'

describe('examAnswers', () => {
  it('parses, trims, deduplicates, and orders selected answers', () => {
    expect(parseMultipleAnswer('3, 1,3')).toEqual(['1', '3'])
  })

  it('serializes selected answers in a stable order', () => {
    expect(serializeMultipleAnswer(['5', '2', '2'])).toBe('2,5')
  })

  it('treats an empty answer as no selection', () => {
    expect(parseMultipleAnswer(null)).toEqual([])
  })
})
