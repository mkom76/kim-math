import client from './client'

export interface PdfRegion { page: number; x: number; y: number; width: number; height: number }
export interface PdfQuestion {
  id: string; position: number; sourceNumber: string; text: string; regions: PdfRegion[]
  suggestedTypeId: string | null; confirmedTypeId: string | null; typePath: string[]
  answer: string | null; points: number | null; format: string | null; multipleAnswers: boolean
  reason: string; needsReview: boolean
}
export interface PdfImport {
  id: string; state: 'PROCESSING' | 'REVIEW' | 'REGISTERED' | 'FAILED' | 'CANCELLED' | 'PURGED'
  version: number; testId: number | null; classId: number; subjectId: string; fileName: string
  pageCount: number; questionPageCount: number; catalogVersion: string; reviewRequired: boolean; expiresAt: string; message: string
  questions: PdfQuestion[]
}
export interface PdfConfig {
  enabled: boolean; ready: boolean; mock: boolean; maxBytes: number; maxPages: number; maxQuestions: number
  catalogVersion: string; notice: string
}
export interface PdfRegistration {
  expectedVersion: number; catalogVersion: string; title: string
  contentsReviewed: boolean
  questions: Array<{ id: string; sourceNumber: string; text: string; regions: PdfRegion[]; typeId: string | null; answer: string | null; points: number | null; format: string | null; multipleAnswers: boolean }>
}
export const examPdfAPI = {
  config: () => client.get<PdfConfig>('/tests/pdf-config'),
  upload: (file: File, classId: number, subjectId: string, requestKey: string, consent: boolean, questionPages: string, solutionPages: string) => {
    const data = new FormData()
    data.append('file', file); data.append('classId', String(classId)); data.append('subjectId', subjectId)
    data.append('requestKey', requestKey); data.append('consent', String(consent))
    data.append('questionPages', questionPages.trim()); data.append('solutionPages', solutionPages.trim())
    return client.post<PdfImport>('/tests/pdf-imports', data, { headers: { 'Content-Type': undefined } })
  },
  get: (id: string) => client.get<PdfImport>(`/tests/pdf-imports/${encodeURIComponent(id)}`),
  document: (id: string) => client.get<Blob>(`/tests/pdf-imports/${encodeURIComponent(id)}/document`, { responseType: 'blob' }),
  register: (id: string, body: PdfRegistration) => client.post<PdfImport>(`/tests/pdf-imports/${encodeURIComponent(id)}/register`, body),
  cancel: (id: string) => client.delete(`/tests/pdf-imports/${encodeURIComponent(id)}`),
}
