import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Enable session cookies
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
  createdAt?: string;
  updatedAt?: string;
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

interface Question {
  id?: number;
  number: number;
  answer: string;
  points: number;
  questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY';
  createdAt?: string;
  updatedAt?: string;
}

interface SubmissionDetail {
  id: number;
  questionNumber: number;
  studentAnswer?: string;
  correctAnswer?: string;
  isCorrect?: boolean;
  earnedPoints?: number;
  maxPoints?: number;
  teacherComment?: string;
  questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY';
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
  role: 'TEACHER' | 'ACADEMY_ADMIN';
}

interface AuthResponse {
  userId?: number;
  name?: string;
  role?: 'STUDENT' | 'TEACHER';
  memberships?: Membership[];
  activeAcademyId?: number;
  activeRole?: 'TEACHER' | 'ACADEMY_ADMIN';
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
};

// Tests API
export const testAPI = {
  getTests: (params?: any) => client.get('/tests', { params }),
  getTest: (id: number) => client.get(`/tests/${id}`),
  createTest: (data: Test) => client.post('/tests', data),
  updateTest: (id: number, data: Test) => client.put(`/tests/${id}`, data),
  deleteTest: (id: number) => client.delete(`/tests/${id}`),
  getTestStats: (id: number) => client.get(`/tests/${id}/stats`),
  getTestQuestions: (id: number) => client.get(`/tests/${id}/questions`),
  addQuestion: (testId: number, data: Omit<Question, 'id'>) =>
    client.post(`/tests/${testId}/questions`, data),
  getUnattachedTests: (academyId: number, classId: number) =>
    client.get('/tests/unattached', { params: { academyId, classId } }),
  recalculateScores: (id: number) => client.post(`/tests/${id}/recalculate`),
  saveTestAnswers: (id: number, answers: Array<{ number: number; answer: string; points: number; questionType?: 'OBJECTIVE' | 'SUBJECTIVE' | 'ESSAY' }>) =>
    client.put(`/tests/${id}/answers`, { testId: id, answers }),
};

// Submissions API
export const submissionAPI = {
  submitAnswers: (studentId: number, testId: number, answers: any) =>
    client.post('/submissions', answers, { params: { studentId, testId } }),
  getByTestId: (testId: number) => client.get(`/submissions/test/${testId}`),
  getStudentSubmissions: (studentId: number) => client.get(`/submissions/student/${studentId}`),
  gradeEssay: (detailId: number, earnedPoints: number, teacherComment?: string) =>
    client.put<SubmissionDetail>(`/submissions/details/${detailId}/grade`, {
      earnedPoints,
      teacherComment,
    }),
  getSubmissionWithDetails: (submissionId: number) =>
    client.get<Submission & { details: SubmissionDetail[] }>(`/submissions/${submissionId}`),
};

// Homeworks API
export const homeworkAPI = {
  getHomeworks: (params?: any) => client.get('/homeworks', { params }),
  createHomework: (data: Homework) => client.post('/homeworks', data),
  updateHomework: (id: number, data: Homework) => client.put(`/homeworks/${id}`, data),
  deleteHomework: (id: number) => client.delete(`/homeworks/${id}`),
  getUnattachedHomeworks: (academyId: number, classId: number) =>
    client.get('/homeworks/unattached', { params: { academyId, classId } }),
};

// Student Homeworks API
export const studentHomeworkAPI = {
  getByStudentId: (studentId: number) => client.get(`/student-homeworks/student/${studentId}`),
  updateIncorrectCount: (studentId: number, homeworkId: number, incorrectCount: number, unsolvedCount: number, incorrectQuestions?: string, unsolvedQuestions?: string) =>
    client.put(`/student-homeworks/student/${studentId}/homework/${homeworkId}`, { incorrectCount, unsolvedCount, incorrectQuestions, unsolvedQuestions }),
  setFollowUp: (studentId: number, homeworkId: number, followUp: boolean) =>
    client.put<StudentHomework>(`/student-homeworks/student/${studentId}/homework/${homeworkId}/follow-up`, { followUp }),
  getFollowUps: (studentId: number) =>
    client.get<StudentHomework[]>(`/student-homeworks/student/${studentId}/follow-ups`),
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
  assignHomeworks: (lessonId: number, assignments: Record<number, number>) =>
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
interface AdminTeacherDto {
  teacherId: number
  name: string
  username: string
  role: 'TEACHER' | 'ACADEMY_ADMIN'
  ownedClassCount: number
}

interface InviteTeacherRequest {
  username: string
  name?: string
  tempPin?: string
  role?: 'TEACHER' | 'ACADEMY_ADMIN'
}

export const adminTeacherAPI = {
  list: () => client.get<AdminTeacherDto[]>('/admin/teachers'),
  invite: (data: InviteTeacherRequest) =>
    client.post<{ teacherId: number; username: string; role: string }>('/admin/teachers', data),
  updateRole: (teacherId: number, role: 'TEACHER' | 'ACADEMY_ADMIN') =>
    client.patch(`/admin/teachers/${teacherId}/role`, { role }),
  remove: (teacherId: number) =>
    client.delete(`/admin/teachers/${teacherId}`),
}

export const adminClassAPI = {
  updateOwner: (classId: number, teacherId: number) =>
    client.patch(`/admin/classes/${classId}/owner`, { teacherId }),
}

// Auth API
export const authAPI = {
  studentLogin: (studentId: number, pin: string) =>
    client.post<AuthResponse>('/auth/student/login', { studentId, pin }),
  teacherLogin: (username: string, pin: string) =>
    client.post<AuthResponse>('/auth/teacher/login', { username, pin }),
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
  SubmissionDetail,
  Homework,
  StudentHomework,
  Lesson,
  LoginDto,
  AuthResponse,
  Membership
};

export type { AdminTeacherDto, InviteTeacherRequest };
