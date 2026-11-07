import type { SubmissionDetail } from '@/api/client'

type IncorrectQuestionDetail = Pick<SubmissionDetail,
  'questionNumber' | 'isCorrect' | 'questionType' | 'earnedPoints' | 'maxPoints'>

export const formatIncorrectQuestionNumbers = (details: IncorrectQuestionDetail[]): string =>
  [...new Set(
    details
      .filter(detail => detail.questionType === 'ESSAY'
        ? detail.earnedPoints != null && detail.maxPoints != null && detail.earnedPoints < detail.maxPoints
        : detail.isCorrect === false)
      .map(detail => detail.questionNumber),
  )]
    .sort((left, right) => left - right)
    .join(',')

export const copyTextToClipboard = async (text: string): Promise<void> => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return
    }
  } catch {
    // Fall through for browsers that expose Clipboard API but deny access.
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()

  let copied = false
  try {
    copied = document.execCommand('copy')
  } finally {
    textarea.remove()
  }
  if (!copied) {
    throw new Error('Clipboard copy failed')
  }
}
