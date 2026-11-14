import client, { type QuestionSnapshot, type SubmissionResult } from './client'

export interface OmrBox { x: number; y: number; width: number; height: number }
export interface OmrMark {
  number: number
  answer: string
  status: 'MARKED' | 'BLANK' | 'MULTIPLE' | 'UNCERTAIN'
  box: OmrBox
}
export interface OmrPage {
  number: number
  recognized: boolean
  message: string
  preview: string
  marks: OmrMark[]
}
export interface OmrScan {
  expectedQuestions: QuestionSnapshot[]
  questions: { number: number; multipleAnswers: boolean }[]
  pages: OmrPage[]
}

export interface OmrReview {
  revision: string
  existing: boolean
  previousScore: number | null
  preservedPoints: number
  maxPoints: number
  preservedQuestionCount: number
  objectives: { number: number; answer: string | null; correctAnswer: string | null; multipleAnswers: boolean; points: number }[]
  expectedQuestions: QuestionSnapshot[]
}

export const omrAPI = {
  scan: (testId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return client.post<OmrScan>(`/tests/${testId}/omr/scan`, body, {
      headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000,
    })
  },
  review: (testId: number, studentId: number) => client.get<OmrReview>(`/tests/${testId}/omr/submissions/${studentId}/review`),
  confirm: (testId: number, studentId: number, answers: Record<number, string>, expectedQuestions: QuestionSnapshot[], expectedRevision: string) =>
    client.put<SubmissionResult>(`/tests/${testId}/omr/submissions/${studentId}`, { answers, expectedQuestions, expectedRevision, reviewed: true }),
}
