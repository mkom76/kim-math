import client from './client'

export interface PdfRegion { page: number; x: number; y: number; width: number; height: number }
export interface PdfQuestion {
  id: string; position: number; sourceNumber: string; text: string; regions: PdfRegion[]
  suggestedTypeId: string | null; confirmedTypeId: string | null; typePath: string[]
  reason: string; needsReview: boolean
}
export interface PdfImport {
  id: string; state: 'PROCESSING' | 'REVIEW' | 'REGISTERED' | 'FAILED' | 'CANCELLED' | 'PURGED'
  version: number; homeworkId: number | null; classId: number; subjectId: string; fileName: string
  pageCount: number; catalogVersion: string; reviewRequired: boolean; expiresAt: string; message: string
  questions: PdfQuestion[]
}
export interface PdfConfig {
  enabled: boolean; ready: boolean; mock: boolean; maxBytes: number; maxPages: number; maxQuestions: number
  catalogVersion: string; notice: string
}
export interface PdfRegistration {
  expectedVersion: number; catalogVersion: string; title: string; memo: string; dueDate: string | null
  contentsReviewed: boolean
  questions: Array<{ id: string; sourceNumber: string; text: string; regions: PdfRegion[]; typeId: string | null }>
}
export const homeworkPdfAPI = {
  config: () => client.get<PdfConfig>('/homeworks/pdf-config'),
  upload: (file: File, classId: number, subjectId: string, requestKey: string, consent: boolean, pages?: string) => {
    const data = new FormData()
    data.append('file', file); data.append('classId', String(classId)); data.append('subjectId', subjectId)
    data.append('requestKey', requestKey); data.append('consent', String(consent))
    if (pages?.trim()) data.append('pages', pages.trim())
    return client.post<PdfImport>('/homeworks/pdf-imports', data, { headers: { 'Content-Type': undefined } })
  },
  get: (id: string) => client.get<PdfImport>(`/homeworks/pdf-imports/${encodeURIComponent(id)}`),
  document: (id: string) => client.get<Blob>(`/homeworks/pdf-imports/${encodeURIComponent(id)}/document`, { responseType: 'blob' }),
  register: (id: string, body: PdfRegistration) => client.post<PdfImport>(`/homeworks/pdf-imports/${encodeURIComponent(id)}/register`, body),
  cancel: (id: string) => client.delete(`/homeworks/pdf-imports/${encodeURIComponent(id)}`),
}
