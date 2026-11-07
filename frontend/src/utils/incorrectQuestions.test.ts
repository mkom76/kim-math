import { describe, expect, it, vi } from 'vitest'
import { copyTextToClipboard, formatIncorrectQuestionNumbers } from './incorrectQuestions'

describe('formatIncorrectQuestionNumbers', () => {
  it('returns incorrect question numbers as a sorted comma-separated string', () => {
    expect(formatIncorrectQuestionNumbers([
      { questionNumber: 7, isCorrect: false },
      { questionNumber: 2, isCorrect: true },
      { questionNumber: 3, isCorrect: false },
      { questionNumber: 1, isCorrect: false },
    ])).toBe('1,3,7')
  })

  it('excludes ungraded essay questions and removes duplicates', () => {
    expect(formatIncorrectQuestionNumbers([
      { questionNumber: 4, isCorrect: null },
      { questionNumber: 2, isCorrect: false },
      { questionNumber: 2, isCorrect: false },
    ])).toBe('2')
  })

  it('returns an empty string when the student has no incorrect answers', () => {
    expect(formatIncorrectQuestionNumbers([
      { questionNumber: 1, isCorrect: true },
      { questionNumber: 2, isCorrect: null },
    ])).toBe('')
  })

  it('includes zero-point and partially credited essays, excluding ungraded and full-credit essays', () => {
    expect(formatIncorrectQuestionNumbers([
      { questionNumber: 6, questionType: 'ESSAY', isCorrect: null, earnedPoints: 0, maxPoints: 10 },
      { questionNumber: 3, questionType: 'ESSAY', isCorrect: null, earnedPoints: 5, maxPoints: 10 },
      { questionNumber: 4, questionType: 'ESSAY', isCorrect: null, earnedPoints: 10, maxPoints: 10 },
      { questionNumber: 5, questionType: 'ESSAY', isCorrect: null, earnedPoints: null, maxPoints: 10 },
      { questionNumber: 7, questionType: 'ESSAY', isCorrect: null, maxPoints: 10 },
      { questionNumber: 1, questionType: 'OBJECTIVE', isCorrect: false },
    ])).toBe('1,3,6')
  })

  it('copies the formatted text with the Clipboard API', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })

    await copyTextToClipboard('1,3,7')

    expect(writeText).toHaveBeenCalledWith('1,3,7')
  })
})
