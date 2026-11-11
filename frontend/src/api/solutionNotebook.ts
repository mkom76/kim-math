import client from './client'
export type NotebookScenario =
  | 'SUCCESS'
  | 'UNREADABLE'
  | 'TIMEOUT'
  | 'RATE_LIMIT'
  | 'MATH_FAILED'
  | 'IMAGE_FAILED'
export type NotebookState =
  | 'QUEUED'
  | 'READING'
  | 'SOLVING'
  | 'VERIFYING'
  | 'RENDERING'
  | 'CHECKING_IMAGE'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REVIEW_REQUIRED'
export interface NotebookConfig {
  enabled: boolean
  mode: 'mock'
  fixture: string
  sampleQuestion: string
  dailySolutionLimit: number
  retentionMinutes: number
  notice: string
}
export interface NotebookSolution {
  title: string
  steps: string[]
  answer: string
  diagram: string
}
export interface NotebookJob {
  id: string
  kind: 'READ' | 'SOLUTION'
  state: NotebookState
  mode: 'mock' | 'live'
  question: string
  uncertainties: string[]
  solution: NotebookSolution | null
  imageAvailable: boolean
  verified: boolean
  message: string
  expiresAt: string
}
export const solutionNotebookAPI = {
  trialConfig: () =>
    client
      .get<{
        ready: boolean
        used: boolean
        mode: 'live'
        notice: string
      }>('/solution-notebook/trial-config')
      .then((r) => r.data),
  photo: (image: Blob, rotation: number) =>
    client
      .post<NotebookJob>('/solution-notebook/photo-jobs', image, {
        params: { rotation },
        headers: { 'Content-Type': image.type, 'X-Notebook-Consent': 'sample-v1' },
      })
      .then((r) => r.data),
  photoStatus: (id: string, signal?: AbortSignal) =>
    client
      .get<NotebookJob>(`/solution-notebook/photo-jobs/${encodeURIComponent(id)}`, { signal })
      .then((r) => r.data),
  photoImage: (id: string) =>
    client
      .get<Blob>(`/solution-notebook/photo-jobs/${encodeURIComponent(id)}/image`, {
        responseType: 'blob',
      })
      .then((r) => r.data),
  photoRemove: (id: string) =>
    client.delete(`/solution-notebook/photo-jobs/${encodeURIComponent(id)}`).then(() => undefined),
  config: () => client.get<NotebookConfig>('/solution-notebook/config').then((r) => r.data),
  read: (fixture: string, scenario: NotebookScenario) =>
    client
      .post<NotebookJob>('/solution-notebook/read-jobs', { fixture, scenario })
      .then((r) => r.data),
  solve: (request: {
    readJobId: string
    question: string
    confirmed: boolean
    grade: string
    depth: 'standard' | 'detailed'
    scenario: NotebookScenario
  }) => client.post<NotebookJob>('/solution-notebook/solution-jobs', request).then((r) => r.data),
  status: (id: string, signal?: AbortSignal) =>
    client
      .get<NotebookJob>(`/solution-notebook/jobs/${encodeURIComponent(id)}`, { signal })
      .then((r) => r.data),
  image: (id: string) =>
    client
      .get<Blob>(`/solution-notebook/jobs/${encodeURIComponent(id)}/image`, {
        responseType: 'blob',
      })
      .then((r) => r.data),
  remove: (id: string) =>
    client.delete(`/solution-notebook/jobs/${encodeURIComponent(id)}`).then(() => undefined),
}
