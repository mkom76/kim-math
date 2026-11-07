import type { Question, QuestionSnapshot } from '@/api/client'

export const createQuestionSnapshot = (questions: Question[]): QuestionSnapshot[] =>
  questions.map(question => ({
    id: question.id ?? null,
    number: question.number,
    updatedAt: question.updatedAt ?? null,
  })).sort((left, right) => left.number - right.number)
