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
  createdAt?: string;
  updatedAt?: string;
}

interface Submission {
  id?: number;
  student?: Student;
  test?: Test;
  testId?: number;
  testTitle?: string;
  totalScore: number;
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
  completion?: number; // 완성도 (계산된 값, 0-100)
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

interface AuthResponse {
  userId?: number;
  name?: string;
  role?: 'STUDENT' | 'TEACHER';
  message?: string;
}

// Academies API
export const academyAPI = {
  getAcademies: (params?: any) => client.get('/academies', { params }),
  getAcademy: (id: number) => client.get(`/academies/${id}`),
  createAcademy: (data: Academy) => client.post('/academies', data),
  updateAcademy: (id: number, data: Academy) => client.put(`/academies/${id}`, data),
  deleteAcademy: (id: number) => client.delete(`/academies/${id}`),
};

// Academy Classes API
export const academyClassAPI = {
  getAcademyClasses: (params?: any) => client.get('/classes', { params }),
  getClassesByAcademy: (academyId: number) => client.get(`/classes/academy/${academyId}`),
  getAcademyClass: (id: number) => client.get(`/classes/${id}`),
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
  deleteQuestion: (id: number) => client.delete(`/questions/${id}`),
  getUnattachedTests: (academyId: number, classId: number) =>
    client.get('/tests/unattached', { params: { academyId, classId } }),
};

// Submissions API
export const submissionAPI = {
  submitAnswers: (studentId: number, testId: number, answers: any) =>
    client.post('/submissions', answers, { params: { studentId, testId } }),
  getSubmission: (id: number) => client.get(`/submissions/${id}`),
  getByTestId: (testId: number) => client.get(`/submissions/test/${testId}`),
  getStudentSubmissions: (studentId: number) => client.get(`/submissions/student/${studentId}`),
};

// Homeworks API
export const homeworkAPI = {
  getHomeworks: (params?: any) => client.get('/homeworks', { params }),
  getHomework: (id: number) => client.get(`/homeworks/${id}`),
  createHomework: (data: Homework) => client.post('/homeworks', data),
  updateHomework: (id: number, data: Homework) => client.put(`/homeworks/${id}`, data),
  deleteHomework: (id: number) => client.delete(`/homeworks/${id}`),
  getUnattachedHomeworks: (academyId: number, classId: number) =>
    client.get('/homeworks/unattached', { params: { academyId, classId } }),
};

// Student Homeworks API
export const studentHomeworkAPI = {
  getByStudentId: (studentId: number) => client.get(`/student-homeworks/student/${studentId}`),
  getByHomeworkId: (homeworkId: number) => client.get(`/student-homeworks/homework/${homeworkId}`),
  updateIncorrectCount: (studentId: number, homeworkId: number, incorrectCount: number, unsolvedCount: number) =>
    client.put(`/student-homeworks/student/${studentId}/homework/${homeworkId}`, { incorrectCount, unsolvedCount }),
  delete: (studentId: number, homeworkId: number) =>
    client.delete(`/student-homeworks/student/${studentId}/homework/${homeworkId}`),
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
  completion?: number;
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
};

// Auth API
export const authAPI = {
  studentLogin: (studentId: number, pin: string) =>
    client.post<AuthResponse>('/auth/student/login', { studentId, pin }),
  teacherLogin: (username: string, pin: string) =>
    client.post<AuthResponse>('/auth/teacher/login', { username, pin }),
  logout: () => client.post('/auth/logout'),
  getCurrentUser: () => client.get<AuthResponse>('/auth/me'),
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

export interface TestFeedback {
  testId: number;
  testTitle: string;
  studentScore: number;
  classAverage: number;
  rank: number;
  incorrectQuestions: number[];
  questionAccuracyRates: QuestionAccuracy[];
}

export interface QuestionAccuracy {
  questionNumber: number;
  correctRate: number;
}

export const dailyFeedbackAPI = {
  getTodayFeedback: (studentId: number) =>
    client.get<DailyFeedback>(`/daily-feedback/student/${studentId}/today`),
  getDailyFeedback: (studentId: number, lessonId: number) =>
    client.get<DailyFeedback>(`/daily-feedback/student/${studentId}/lesson/${lessonId}`),
  updateInstructorFeedback: (studentId: number, lessonId: number, feedback: string, authorName: string) =>
    client.put(`/daily-feedback/student/${studentId}/lesson/${lessonId}`, { feedback, authorName }),
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
  completion?: number;
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
  averageCompletionBefore: number;
  averageCompletionAfter: number;
  averageCompletionChange: number;
  homeworks: RecentClinicHomework[];
}

export interface RecentClinicHomework {
  homeworkId: number;
  homeworkTitle: string;
  completionBefore: number;
  completionAfter: number;
  completionChange: number;
}

export const clinicAPI = {
  createClinicForThisWeek: (classId: number) =>
    client.post<Clinic>(`/clinics/class/${classId}/create-for-this-week`),
  createClinic: (classId: number, clinicDate: string, clinicTime: string) =>
    client.post<Clinic>(`/clinics/class/${classId}`, { clinicDate, clinicTime }),
  getClinicsByClass: (classId: number) =>
    client.get<Clinic[]>(`/clinics/class/${classId}`),
  getUpcomingClinic: (classId: number) =>
    client.get<Clinic>(`/clinics/class/${classId}/upcoming`),
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
  closeClinic: (clinicId: number) =>
    client.put<Clinic>(`/clinics/${clinicId}/close`),
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

export default client;

export type {
  Academy,
  AcademyClass,
  Student,
  Test,
  Question,
  Submission,
  Homework,
  StudentHomework,
  Lesson,
  LoginDto,
  AuthResponse,
  DailyFeedback,
  HomeworkSummary,
  TestFeedback,
  QuestionAccuracy,
  StudentHomeworkAssignment,
  LessonStudentStats,
  StudentTestScore,
  StudentHomeworkCompletion,
  Clinic,
  ClinicRegistration,
  ClinicDetail,
  StudentClinicHomework,
  HomeworkProgress,
  StudentClinicInfo,
  IncompleteHomework,
  ClinicHomeworkProgress,
  RecentClinicResult,
  RecentClinicHomework
};