import axios, { AxiosHeaders, type AxiosError, type InternalAxiosRequestConfig } from 'axios';

// Dev (emulator) reaches the host backend via `adb reverse tcp:8080 tcp:8080`,
// which maps the emulator's localhost:8080 to the host machine. For browser
// dev, use the same hostname the frontend was opened with (localhost vs
// 127.0.0.1) so the session cookie stays same-site.
// Production sets VITE_API_BASE_URL to the live origin.
const defaultApiBaseUrl =
  typeof window !== 'undefined'
    ? `http://${window.location.hostname}:8080/api`
    : 'http://localhost:8080/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || defaultApiBaseUrl;

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Enable session cookies
  // Spring Security 6 exposes a masked CSRF token from /auth/csrf. Axios'
  // built-in XSRF support reads the raw XSRF-TOKEN cookie later in the adapter
  // phase and would overwrite the masked token set by our interceptor.
  withXSRFToken: false,
});

const CSRF_SAFE_METHODS = new Set(['get', 'head', 'options', 'trace']);
interface CsrfResponse {
  headerName: string;
  token: string;
}

let csrfReady: Promise<CsrfResponse> | null = null;

function isUnsafeMethod(method?: string): boolean {
  return !CSRF_SAFE_METHODS.has((method || 'get').toLowerCase());
}

function csrfEndpoint(): string {
  return `${API_BASE_URL}/auth/csrf`;
}

async function ensureCsrfToken(): Promise<CsrfResponse | null> {
  if (typeof window === 'undefined') return null;
  if (!csrfReady) {
    csrfReady = axios.get<CsrfResponse>(csrfEndpoint(), { withCredentials: true })
      .then(response => response.data);
  }
  return csrfReady;
}

client.interceptors.request.use(async config => {
  if (isUnsafeMethod(config.method)) {
    const csrf = await ensureCsrfToken();
    if (csrf?.headerName && csrf.token) {
      config.headers = AxiosHeaders.from(config.headers);
      config.headers.set(csrf.headerName, csrf.token);
    }
  }
  return config;
});

client.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as (InternalAxiosRequestConfig & { _csrfRetry?: boolean }) | undefined;
  if (error.response?.status === 403 && config && isUnsafeMethod(config.method) && !config._csrfRetry) {
    config._csrfRetry = true;
    csrfReady = null;
    await ensureCsrfToken();
    return client(config);
  }
  throw error;
});

// Types
interface Academy {
  id?: number;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

interface AcademyClass {
  id?: number;
  name: string;
  academyId?: number;
  academyName?: string;
  clinicDayOfWeek?: string; // MONDAY, TUESDAY, ...
  clinicTime?: string; // HH:mm:ss
  createdAt?: string;
  updatedAt?: string;
}

export type StudentStatus = 'PENDING_CONSENT' | 'ACTIVE' | 'REVOKED';

interface Student {
  id?: number;
  name: string;
  grade: string;
  school: string;
  academyId?: number;
  academyName?: string;
  classId?: number;
  className?: string;
  pin?: string;
  parentName?: string;
  parentPhone?: string;
  contactPhone?: string;
  status?: StudentStatus;
  hideScoresFromStudent?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface StudentBulkCreateItem {
  name: string;
  grade: string;
  school: string;
  parentName: string;
  parentPhone: string;
  contactPhone?: string;
}

export interface StudentBulkCreateRequest {
  classId: number;
  students: StudentBulkCreateItem[];
}

export interface StudentBulkCreateResultItem {
  studentId: number;
  name: string;
  parentName: string;
  parentPhone: string;
  consentToken: string;
}

export interface StudentBulkCreateResponse {
  created: StudentBulkCreateResultItem[];
}

export interface ConsentInfo {
  consentVersion: string;
  studentName: string;
  parentName: string;
  parentPhoneMasked: string;
  academyName?: string;
  className?: string;
  alreadyConsented: boolean;
  expired: boolean;
}

interface Test {
  id?: number;
  title: string;
  academyId?: number;
  academyName?: string;
  classId?: number;
  className?: string;
  questionCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TextbookProblemMeta {
  id: number;
  textbookId: number;
  number: number;
  topic?: string | null;
  videoLink?: string | null;
}

interface Question {
  id?: number;
  number: number;
  answer: string;
  points: number;
  questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY';
  textbookProblem?: TextbookProblemMeta | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface SubmissionDetail {
  id: number;
  questionNumber: number;
  studentAnswer?: string;
  correctAnswer?: string;
  isCorrect?: boolean | null;
  earnedPoints?: number;
  maxPoints?: number;
  teacherComment?: string;
  questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY';
  topic?: string | null;
  videoLink?: string | null;
}

export interface SubmissionResult {
  id: number;
  testId: number;
  testTitle: string;
  totalScore: number;
  classAverage?: number;
  rank?: number;
  pendingEssayCount?: number;
  submittedAt?: string;
  student?: Student;
  details: SubmissionDetail[];
}

interface Submission {
  id?: number;
  student?: Student;
  test?: Test;
  testId?: number;
  testTitle?: string;
  totalScore: number;
  pendingEssayCount?: number;
  classAverage?: number;
  rank?: number;
  submittedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface TestSubmissionRoster {
  studentId: number;
  studentName: string;
  grade?: string;
  school?: string;
  submitted: boolean;
  submissionId?: number | null;
  score?: number | null;
  pendingEssayCount?: number | null;
  submittedAt?: string | null;
}

interface Homework {
  id?: number;
  title: string;
  questionCount: number;
  memo?: string;
  dueDate?: string;
  academyId?: number;
  academyName?: string;
  classId?: number;
  className?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface StudentHomework {
  id?: number;
  studentId?: number;
  studentName?: string;
  homeworkId?: number;
  homeworkTitle?: string;
  questionCount?: number; // 전체 문제 수
  dueDate?: string; // 제출 기한
  incorrectCount?: number; // 오답 개수
  unsolvedCount?: number; // 안 푼 문제 개수
  incorrectQuestions?: string; // 오답 문항번호
  unsolvedQuestions?: string; // 안 푼 문항번호
  questionedQuestions?: string; // 학생이 질문하고 싶은 문항번호
  completion?: number; // 완성도 (계산된 값, 0-100)
  followUpFlag?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

interface Lesson {
  id?: number;
  lessonDate: string;
  academyId?: number;
  academyName?: string;
  classId?: number;
  className?: string;
  testId?: number;
  testTitle?: string;
  homeworkId?: number;
  homeworkTitle?: string;
  commonFeedback?: string;
  announcement?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface LoginDto {
  username?: string; // for teacher
  studentId?: number; // for student
  pin: string;
}

interface Membership {
  academyId: number;
  academyName: string;
  role: 'TEACHER' | 'ACADEMY_ADMIN' | 'ASSISTANT';
}

interface AuthResponse {
  userId?: number;
  name?: string;
  role?: 'STUDENT' | 'TEACHER';
  memberships?: Membership[];
  activeAcademyId?: number;
  activeRole?: 'TEACHER' | 'ACADEMY_ADMIN' | 'ASSISTANT';
  message?: string;
}

// Academies API
export const academyAPI = {
  getAcademies: (params?: any) => client.get('/academies', { params }),
  updateAcademy: (id: number, data: Academy) => client.put(`/academies/${id}`, data),
};

// Academy Classes API
export const academyClassAPI = {
  getAcademyClasses: (params?: any) => client.get('/classes', { params }),
  createAcademyClass: (data: AcademyClass) => client.post('/classes', data),
  updateAcademyClass: (id: number, data: AcademyClass) => client.put(`/classes/${id}`, data),
  deleteAcademyClass: (id: number) => client.delete(`/classes/${id}`),
};

// Students API
export const studentAPI = {
  getStudents: (params?: any) => client.get('/students', { params }),
  getStudent: (id: number) => client.get(`/students/${id}`),
  createStudent: (data: Student) => client.post('/students', data),
  updateStudent: (id: number, data: Student) => client.put(`/students/${id}`, data),
  deleteStudent: (id: number) => client.delete(`/students/${id}`),
  resetPin: (id: number, pin: string) => client.put(`/students/${id}/reset-pin`, { pin }),
  setScoreVisibility: (id: number, hide: boolean) =>
    client.put<Student>(`/students/${id}/score-visibility`, { hide }),
  bulkCreate: (data: StudentBulkCreateRequest) =>
    client.post<StudentBulkCreateResponse>('/students/bulk', data),
};

// Public consent API (no auth — token-gated)
export const consentAPI = {
  get: (token: string) => client.get<ConsentInfo>(`/consents/${token}`),
  agree: (token: string, parentPhoneLast4: string) =>
    client.post(`/consents/${token}/agree`, { parentPhoneLast4 }),
};

// Device token registration for push notifications (student-side)
export const deviceAPI = {
  register: (data: { token: string; platform: 'android' | 'ios'; appVersion?: string }) =>
    client.post('/devices/register', data),
  unregister: (token: string) =>
    client.delete(`/devices/${encodeURIComponent(token)}`),
};

// Tests API
export const testAPI = {
  getTests: (params?: any) => client.get('/tests', { params }),
  getTest: (id: number) => client.get(`/tests/${id}`),
  createTest: (data: Test) => client.post('/tests', data),
  updateTest: (id: number, data: Test) => client.put(`/tests/${id}`, data),
  deleteTest: (id: number) => client.delete(`/tests/${id}`),
  getTestStats: (id: number) => client.get(`/tests/${id}/stats`),
  getSubmissionRoster: (id: number) =>
    client.get<TestSubmissionRoster[]>(`/tests/${id}/submission-roster`),
  getTestQuestions: (id: number) => client.get(`/tests/${id}/questions`),
  addQuestion: (testId: number, data: Omit<Question, 'id'>) =>
    client.post(`/tests/${testId}/questions`, data),
  getUnattachedTests: (academyId: number, classId: number) =>
    client.get('/tests/unattached', { params: { academyId, classId } }),
  recalculateScores: (id: number) => client.post(`/tests/${id}/recalculate`),
  saveTestAnswers: (id: number, answers: Array<{ number: number; answer: string; points: number; questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY' }>) =>
    client.put(`/tests/${id}/answers`, { testId: id, answers }),
  addQuestionsFromTextbook: (testId: number, items: Array<{ textbookProblemId: number; number: number; points: number }>) =>
    client.post(`/tests/${testId}/questions/from-textbook`, items),
};

// Submissions API
export const submissionAPI = {
  submitAnswers: (studentId: number, testId: number, answers: any) =>
    client.post('/submissions', answers, { params: { studentId, testId } }),
  submitMyAnswers: (testId: number, answers: any) =>
    client.post('/submissions/me/test/' + testId, answers),
  saveAnswersForStudent: (studentId: number, testId: number, answers: any) =>
    client.put<SubmissionResult>(`/submissions/students/${studentId}/tests/${testId}`, answers),
  getByTestId: (testId: number) => client.get(`/submissions/test/${testId}`),
  getStudentSubmissions: (studentId: number) => client.get(`/submissions/student/${studentId}`),
  gradeEssay: (detailId: number, earnedPoints: number, teacherComment?: string) =>
    client.put<SubmissionDetail>(`/submissions/details/${detailId}/grade`, {
      earnedPoints,
      teacherComment,
    }),
  getSubmissionWithDetails: (submissionId: number) =>
    client.get<Submission & { details: SubmissionDetail[] }>(`/submissions/${submissionId}`),
  getMyResultByTest: (testId: number) =>
    client.get<SubmissionResult>(`/submissions/me/test/${testId}`),
  getResultByStudentAndTest: (studentId: number, testId: number) =>
    client.get<SubmissionResult>(`/submissions`, { params: { studentId, testId } }),
};

// Homeworks API
export const homeworkAPI = {
  getHomeworks: (params?: any) => client.get('/homeworks', { params }),
  createHomework: (data: Homework) => client.post('/homeworks', data),
  updateHomework: (id: number, data: Homework) => client.put(`/homeworks/${id}`, data),
  deleteHomework: (id: number) => client.delete(`/homeworks/${id}`),
  getUnattachedHomeworks: (academyId: number, classId: number) =>
    client.get('/homeworks/unattached', { params: { academyId, classId } }),
  getProblems: (id: number) =>
    client.get<HomeworkProblemRow[]>(`/homeworks/${id}/problems`),
  replaceProblems: (id: number, items: Array<{ textbookProblemId: number | null }>) =>
    client.put<HomeworkProblemRow[]>(`/homeworks/${id}/problems`, items),
};

export interface HomeworkProblemRow {
  id: number;
  position: number;
  textbookProblem?: TextbookProblemMeta | null;
}

// Student Homeworks API
export const studentHomeworkAPI = {
  getByStudentId: (studentId: number) => client.get(`/student-homeworks/student/${studentId}`),
  updateIncorrectCount: (studentId: number, homeworkId: number, incorrectCount: number, unsolvedCount: number, incorrectQuestions?: string, unsolvedQuestions?: string) =>
    client.put(`/student-homeworks/student/${studentId}/homework/${homeworkId}`, { incorrectCount, unsolvedCount, incorrectQuestions, unsolvedQuestions }),
  setFollowUp: (studentId: number, homeworkId: number, followUp: boolean) =>
    client.put<StudentHomework>(`/student-homeworks/student/${studentId}/homework/${homeworkId}/follow-up`, { followUp }),
  getFollowUps: (studentId: number) =>
    client.get<StudentHomework[]>(`/student-homeworks/student/${studentId}/follow-ups`),
  updateQuestionedQuestions: (studentId: number, homeworkId: number, questionedQuestions: string) =>
    client.put<StudentHomework>(`/student-homeworks/student/${studentId}/homework/${homeworkId}/questioned-questions`, { questionedQuestions }),
};

// Textbooks API
export type TextbookQuestionType = 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY';

export interface TextbookProblem {
  id?: number;
  textbookId?: number;
  number: number;
  answer?: string | null;
  questionType?: TextbookQuestionType | null;
  /** Canonical path joined with " › ", e.g. "함수 › 일차함수". */
  topic?: string | null;
  /** Same path as 1..5 segments — convenient for breadcrumb / filters. */
  topicLevels?: string[];
  videoLink?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface TopicSuggestionsQuery {
  level: number;
  l1?: string;
  l2?: string;
  l3?: string;
  l4?: string;
}

export interface Textbook {
  id?: number;
  ownerTeacherId?: number;
  title: string;
  problemCount?: number;
  problems?: TextbookProblem[];
  createdAt?: string;
  updatedAt?: string;
}

export const textbookAPI = {
  list: () => client.get<Textbook[]>('/textbooks'),
  get: (id: number) => client.get<Textbook>(`/textbooks/${id}`),
  create: (title: string) => client.post<Textbook>('/textbooks', { title }),
  update: (id: number, title: string) => client.put<Textbook>(`/textbooks/${id}`, { title }),
  remove: (id: number) => client.delete(`/textbooks/${id}`),

  listProblems: (textbookId: number) =>
    client.get<TextbookProblem[]>(`/textbooks/${textbookId}/problems`),
  createProblem: (textbookId: number, data: TextbookProblem) =>
    client.post<TextbookProblem>(`/textbooks/${textbookId}/problems`, data),
  updateProblem: (id: number, data: TextbookProblem) =>
    client.put<TextbookProblem>(`/textbook-problems/${id}`, data),
  deleteProblem: (id: number) => client.delete(`/textbook-problems/${id}`),
  bulkCreateProblems: (textbookId: number, items: Array<Partial<TextbookProblem>>) =>
    client.post<TextbookProblem[]>(`/textbooks/${textbookId}/problems/bulk`, items),

  topicSuggestions: (textbookId: number, query: TopicSuggestionsQuery) =>
    client.get<string[]>(`/textbooks/${textbookId}/topic-suggestions`, { params: query }),
};

// Lesson Student Stats Types
export interface LessonStudentStats {
  testScores?: StudentTestScore[];
  testAverage?: number;
  homeworkCompletions?: StudentHomeworkCompletion[];
  homeworkAverage?: number;
}

export interface StudentTestScore {
  studentId: number;
  studentName: string;
  score?: number;
  rank?: number;
  submitted: boolean;
}

export interface StudentHomeworkCompletion {
  studentId: number;
  studentName: string;
  incorrectCount?: number;
  unsolvedCount?: number;
  incorrectQuestions?: string;
  unsolvedQuestions?: string;
  completion?: number;
  completed: boolean;
  totalQuestions: number;
}

export interface StudentHomeworkAssignment {
  studentId: number;
  studentName: string;
  assignedHomeworkId?: number;
  assignedHomeworkTitle?: string;
  incorrectCount?: number;
  unsolvedCount?: number;
  incorrectQuestions?: string;
  unsolvedQuestions?: string;
  completion?: number;
  followUpFlag?: boolean;
}

// Attendance types
export interface AttendanceRecord {
  studentId: number
  studentName: string
  attendanceStatus: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'VIDEO' | null
}

export interface AttendanceRequest {
  studentId: number
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EARLY_LEAVE' | 'VIDEO' | null
}

export interface AttendanceStats {
  studentId: number
  studentName: string
  totalLessons: number
  presentCount: number
  absentCount: number
  lateCount: number
  earlyLeaveCount: number
  videoCount: number
  uncheckedCount: number
  attendanceRate: number
}

// Lessons API
export const lessonAPI = {
  getLessons: (params?: any) => client.get<{ content: Lesson[] }>('/lessons', { params }),
  getLesson: (id: number) => client.get<Lesson>(`/lessons/${id}`),
  getLessonsByClass: (classId: number) => client.get<Lesson[]>(`/lessons/class/${classId}`),
  getLessonsByStudent: (studentId: number) => client.get<Lesson[]>(`/lessons/student/${studentId}`),
  createLesson: (data: { academyId: number; classId: number; lessonDate: string }) => client.post('/lessons', data),
  attachTest: (lessonId: number, testId: number) => client.put(`/lessons/${lessonId}/test/${testId}`),
  attachHomework: (lessonId: number, homeworkId: number) => client.put(`/lessons/${lessonId}/homework/${homeworkId}`),
  detachTest: (lessonId: number) => client.delete(`/lessons/${lessonId}/test`),
  deleteLesson: (id: number) => client.delete(`/lessons/${id}`),
  getLessonStats: (lessonId: number) => client.get<LessonStudentStats>(`/lessons/${lessonId}/stats`),
  updateLessonContent: (lessonId: number, commonFeedback: string, announcement: string) =>
    client.put<Lesson>(`/lessons/${lessonId}/content`, { commonFeedback, announcement }),
  updateLessonDate: (lessonId: number, lessonDate: string) =>
    client.put<Lesson>(`/lessons/${lessonId}/date`, { lessonDate }),

  // 새로운 숙제 관리 API
  getLessonHomeworks: (lessonId: number) => client.get<Homework[]>(`/lessons/${lessonId}/homeworks`),
  removeHomework: (lessonId: number, homeworkId: number) => client.delete(`/lessons/${lessonId}/homeworks/${homeworkId}`),

  // 숙제 할당 API
  assignHomeworks: (lessonId: number, assignments: Record<number, number | null>) =>
    client.post(`/lessons/${lessonId}/assign-homeworks`, assignments),
  getAssignments: (lessonId: number) => client.get<StudentHomeworkAssignment[]>(`/lessons/${lessonId}/assignments`),

  // 출석 API
  getAttendance: (lessonId: number) =>
    client.get<AttendanceRecord[]>(`/lessons/${lessonId}/attendance`).then(res => res.data),
  saveAttendance: (lessonId: number, attendanceList: AttendanceRequest[]) =>
    client.put(`/lessons/${lessonId}/attendance`, attendanceList),
  getAttendanceStats: (studentId: number) =>
    client.get<AttendanceStats>(`/lessons/attendance/student/${studentId}`).then(res => res.data),
};

// Admin API
export type AdminRole = 'TEACHER' | 'ACADEMY_ADMIN' | 'ASSISTANT'

interface AdminTeacherDto {
  teacherId: number
  name: string
  username: string
  role: AdminRole
  ownedClassCount: number
}

interface InviteTeacherRequest {
  username: string
  name?: string
  tempPin?: string
  role?: AdminRole
}

export const adminTeacherAPI = {
  list: () => client.get<AdminTeacherDto[]>('/admin/teachers'),
  invite: (data: InviteTeacherRequest) =>
    client.post<{ teacherId: number; username: string; role: string }>('/admin/teachers', data),
  updateRole: (teacherId: number, role: AdminRole) =>
    client.patch(`/admin/teachers/${teacherId}/role`, { role }),
  remove: (teacherId: number) =>
    client.delete(`/admin/teachers/${teacherId}`),
}

export const adminClassAPI = {
  updateOwner: (classId: number, teacherId: number) =>
    client.patch(`/admin/classes/${classId}/owner`, { teacherId }),
  listAssistants: () =>
    client.get<Record<string, number[]>>('/admin/classes/assistants'),
  addAssistant: (classId: number, teacherId: number) =>
    client.post(`/admin/classes/${classId}/assistants`, { teacherId }),
  removeAssistant: (classId: number, teacherId: number) =>
    client.delete(`/admin/classes/${classId}/assistants/${teacherId}`),
}

// Auth API
export const authAPI = {
  studentLogin: (studentId: number, pin: string, rememberMe?: boolean) =>
    client.post<AuthResponse>('/auth/student/login', { studentId, pin, rememberMe }),
  teacherLogin: (username: string, pin: string, rememberMe?: boolean) =>
    client.post<AuthResponse>('/auth/teacher/login', { username, pin, rememberMe }),
  logout: () => client.post('/auth/logout'),
  getCurrentUser: () => client.get<AuthResponse>('/auth/me'),
  switchAcademy: (academyId: number) =>
    client.post<AuthResponse>('/auth/switch-academy', { academyId }),
  changePin: (currentPin: string, newPin: string) =>
    client.put<AuthResponse>('/auth/change-pin', { currentPin, newPin }),
};

// Daily Feedback API
export interface DailyFeedback {
  lessonId: number;
  lessonDate: string;
  todayHomework?: HomeworkSummary;
  nextHomework?: HomeworkSummary;
  todayTest?: TestFeedback;
  instructorFeedback?: string;
  feedbackAuthor?: string;
}

export interface HomeworkSummary {
  homeworkId: number;
  homeworkTitle: string;
  questionCount: number;
  incorrectCount?: number;
  unsolvedCount?: number;
  incorrectQuestions?: string;
  unsolvedQuestions?: string;
  questionedQuestions?: string;
  completion?: number;
  dueDate?: string;
}

export interface EssayDetail {
  questionNumber: number;
  maxPoints: number;
  studentAnswer?: string;
  earnedPoints?: number;    // null = 미채점
  teacherComment?: string;
}

export interface TestFeedback {
  testId: number;
  testTitle: string;
  studentScore: number;
  classAverage: number;
  rank: number;
  incorrectQuestions: number[];
  questionAccuracyRates: QuestionAccuracy[];
  essayDetails?: EssayDetail[];
}

export interface QuestionAccuracy {
  questionNumber: number;
  correctRate: number;
}

export const dailyFeedbackAPI = {
  getDailyFeedback: (studentId: number, lessonId: number) =>
    client.get<DailyFeedback>(`/daily-feedback/student/${studentId}/lesson/${lessonId}`),
  updateInstructorFeedback: (studentId: number, lessonId: number, feedback: string, authorName: string, isAiFeedback?: boolean) =>
    client.put(`/daily-feedback/student/${studentId}/lesson/${lessonId}`, { feedback, authorName, isAiFeedback }),
  // Push "오늘의 피드백이 도착했어요" to every student in the lesson who has feedback written.
  notifyLesson: (lessonId: number) =>
    client.post<{ sentCount: number }>(`/daily-feedback/lesson/${lessonId}/notify`),
};

// Clinic API
export interface Clinic {
  id?: number;
  classId?: number;
  className?: string;
  academyId?: number;
  academyName?: string;
  clinicDate: string;
  clinicTime: string;
  status: 'OPEN' | 'CLOSED';
  registrationCount?: number;
}

export interface ClinicRegistration {
  id?: number;
  clinicId?: number;
  studentId?: number;
  studentName?: string;
  status: 'REGISTERED' | 'ATTENDED' | 'CANCELLED';
  createdAt?: string;
}

export interface ClinicDetail {
  clinic: Clinic;
  students: StudentClinicHomework[];
}

export interface StudentClinicHomework {
  studentId: number;
  studentName: string;
  registration?: ClinicRegistration;
  homeworks: HomeworkProgress[];
}

export interface HomeworkProgress {
  homeworkId: number;
  homeworkTitle: string;
  questionCount: number;
  incorrectCount?: number;
  unsolvedCount?: number;
  incorrectQuestions?: string;
  unsolvedQuestions?: string;
  completion?: number;
  followUpFlag?: boolean;
  lessonId?: number;
  lessonDate?: string;
}

export interface StudentClinicInfo {
  upcomingClinic?: Clinic;
  myRegistration?: ClinicRegistration;
  shouldAttend: boolean;
  incompleteHomeworks: IncompleteHomework[];
}

export interface IncompleteHomework {
  homeworkId: number;
  homeworkTitle: string;
  completion?: number;
  lessonDate?: string;
}

export interface ClinicHomeworkProgress {
  id?: number;
  clinicId: number;
  studentId: number;
  studentName: string;
  homeworkId: number;
  homeworkTitle: string;
  homeworkQuestionCount: number;
  incorrectCountBefore?: number;
  unsolvedCountBefore?: number;
  completionBefore?: number;
  incorrectCountAfter?: number;
  unsolvedCountAfter?: number;
  completionAfter?: number;
  completionChange?: number;
}

export interface RecentClinicResult {
  clinicId: number;
  clinicDate: string;
  clinicTime: string;
  improvedHomeworkCount: number;
  totalIncorrectCountBefore: number;
  totalIncorrectCountAfter: number;
  totalIncorrectCountChange: number;
  totalUnsolvedCountBefore: number;
  totalUnsolvedCountAfter: number;
  totalUnsolvedCountChange: number;
  homeworks: RecentClinicHomework[];
}

export interface RecentClinicHomework {
  homeworkId: number;
  homeworkTitle: string;
  incorrectCountBefore: number;
  incorrectCountAfter: number;
  incorrectCountChange: number;
  unsolvedCountBefore: number;
  unsolvedCountAfter: number;
  unsolvedCountChange: number;
}

export const clinicAPI = {
  createClinic: (classId: number, clinicDate: string, clinicTime: string) =>
    client.post<Clinic>(`/clinics/class/${classId}`, { clinicDate, clinicTime }),
  getClinicsByClass: (classId: number) =>
    client.get<Clinic[]>(`/clinics/class/${classId}`),
  getClinicDetail: (clinicId: number) =>
    client.get<ClinicDetail>(`/clinics/${clinicId}/detail`),
  registerForClinic: (clinicId: number, studentId: number) =>
    client.post<ClinicRegistration>(`/clinics/${clinicId}/register`, { studentId }),
  cancelRegistration: (clinicId: number, studentId: number) =>
    client.delete(`/clinics/${clinicId}/register/${studentId}`),
  updateAttendance: (registrationId: number, status: string) =>
    client.put<ClinicRegistration>(`/clinics/registrations/${registrationId}/attendance`, { status }),
  getStudentClinicInfo: (studentId: number) =>
    client.get<StudentClinicInfo>(`/clinics/student/${studentId}/info`),
  deleteClinic: (clinicId: number) =>
    client.delete(`/clinics/${clinicId}`),
  startClinic: (clinicId: number) =>
    client.post(`/clinics/${clinicId}/start`),
  endClinic: (clinicId: number) =>
    client.post(`/clinics/${clinicId}/end`),
  getClinicProgress: (clinicId: number) =>
    client.get<ClinicHomeworkProgress[]>(`/clinics/${clinicId}/progress`),
  getRecentClinicResult: (studentId: number) =>
    client.get<RecentClinicResult>(`/clinics/student/${studentId}/recent-result`),
};

// LessonVideo types and API
export interface LessonVideo {
  id: number
  lessonId: number
  youtubeUrl: string
  youtubeVideoId: string
  title: string
  thumbnailUrl: string
  duration: string
  orderIndex: number
  createdAt: string
}

export interface StudentLessonVideos {
  lessonId: number
  lessonDate: string
  className: string
  videos: LessonVideo[]
}

export interface VideoStats {
  videoId: number
  title: string
  studentProgress: StudentProgress[]
}

export interface StudentProgress {
  studentId: number
  studentName: string
  progressPercent: number
  completed: boolean
  lastWatchedAt?: string
}

export const lessonVideoAPI = {
  getVideos: (lessonId: number) =>
    client.get<LessonVideo[]>(`/lessons/${lessonId}/videos`),

  addVideo: (lessonId: number, data: { youtubeUrl: string }) =>
    client.post<LessonVideo>(`/lessons/${lessonId}/videos`, data),

  updateOrder: (lessonId: number, videoId: number, orderIndex: number) =>
    client.put<LessonVideo>(`/lessons/${lessonId}/videos/${videoId}/order`, { orderIndex }),

  deleteVideo: (lessonId: number, videoId: number) =>
    client.delete(`/lessons/${lessonId}/videos/${videoId}`),

  getVideoStats: (lessonId: number) =>
    client.get<VideoStats[]>(`/lessons/${lessonId}/videos/stats`)
}

export const studentVideoAPI = {
  getVideos: (studentId: number) =>
    client.get<StudentLessonVideos[]>(`/students/${studentId}/videos`)
}

// Video Progress types and API
export interface VideoProgressUpdate {
  watchedTime: number
  duration: number
}

export interface VideoProgress {
  videoId: number
  watchedTime: number
  duration: number
  progressPercent: number
  completed: boolean
  lastWatchedAt: string
}

export const videoProgressAPI = {
  updateProgress: (studentId: number, videoId: number, data: VideoProgressUpdate) =>
    client.put<VideoProgress>(`/students/${studentId}/videos/${videoId}/progress`, data),

  getStudentProgress: (studentId: number) =>
    client.get<VideoProgress[]>(`/students/${studentId}/videos/progress`)
}

// AI Feedback API
export interface AiFeedbackRequest {
  studentId: number
  lessonId: number
  teacherId: number
  model?: string
}

export interface AiFeedbackResponse {
  generatedFeedback: string
  studentId: number
  lessonId: number
}

export interface FeedbackPromptTemplate {
  id?: number
  teacherId: number
  systemPrompt: string
  fewShotCount: number
  isActive: boolean
}

export interface BulkAiFeedbackRequest {
  lessonId: number
  teacherId: number
  model?: string
}

export interface BulkAiFeedbackResponse {
  status: 'PROCESSING' | 'COMPLETED'
  totalCount: number
  processedCount: number
  successCount: number
  failCount: number
  skippedCount: number
  skippedStudents: string[]
}

export const aiFeedbackAPI = {
  generate: (request: AiFeedbackRequest) =>
    client.post<AiFeedbackResponse>('/ai-feedback/generate', request).then(res => res.data),
  generateBulk: (request: BulkAiFeedbackRequest) =>
    client.post<BulkAiFeedbackResponse>('/ai-feedback/generate-bulk', request).then(res => res.data),
  getBulkStatus: (lessonId: number) =>
    client.get<BulkAiFeedbackResponse>(`/ai-feedback/generate-bulk/status/${lessonId}`).then(res => res.data),
}

export const feedbackPromptTemplateAPI = {
  getByTeacher: (teacherId: number) =>
    client.get<FeedbackPromptTemplate>(`/feedback-prompt-templates/teacher/${teacherId}`).then(res => res.data),
  save: (teacherId: number, template: FeedbackPromptTemplate) =>
    client.put<FeedbackPromptTemplate>(`/feedback-prompt-templates/teacher/${teacherId}`, template).then(res => res.data),
}

export default client;

export type {
  Academy,
  AcademyClass,
  Student,
  Test,
  Question,
  Submission,
  TestSubmissionRoster,
  Homework,
  StudentHomework,
  Lesson,
  LoginDto,
  AuthResponse,
  Membership
};

export type { AdminTeacherDto, InviteTeacherRequest };
