import { createRouter, createWebHistory } from 'vue-router'
import { authAPI } from '@/api/client'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      redirect: '/students'
    },
    {
      path: '/students',
      name: 'students',
      component: () => import('../views/StudentsView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/students/:id',
      name: 'student-detail',
      component: () => import('../views/StudentDetailView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/students/:id/feedback',
      name: 'student-feedback',
      component: () => import('../views/StudentDailyFeedbackView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/tests',
      name: 'tests',
      component: () => import('../views/TestsView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/tests/:id',
      name: 'test-detail',
      component: () => import('../views/TestDetailView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/tests/:id/answers',
      name: 'test-answers',
      component: () => import('../views/TestAnswersView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/homeworks',
      name: 'homeworks',
      component: () => import('../views/HomeworksView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/lessons',
      name: 'lessons',
      component: () => import('../views/LessonsView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/lessons/:id',
      name: 'lesson-detail',
      component: () => import('../views/LessonDetailView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/academies',
      name: 'academies',
      component: () => import('../views/AcademiesView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/academy-classes',
      name: 'academy-classes',
      component: () => import('../views/AcademyClassesView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/clinics',
      name: 'clinics',
      component: () => import('../views/ClinicsView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/clinics/:id',
      name: 'clinic-detail',
      component: () => import('../views/ClinicDetailView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/student/dashboard',
      name: 'student-dashboard',
      component: () => import('../views/StudentDashboardView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
    {
      path: '/student/tests/:id',
      name: 'student-test-taking',
      component: () => import('../views/StudentTestTakingView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
    {
      path: '/student/daily-feedback',
      name: 'student-daily-feedback',
      component: () => import('../views/StudentDailyFeedbackView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
    {
      path: '/student/stats',
      name: 'student-stats',
      component: () => import('../views/StudentDetailView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
    {
      path: '/student/clinic',
      name: 'student-clinic',
      component: () => import('../views/StudentClinicView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
    {
      path: '/student/videos',
      name: 'student-videos',
      component: () => import('../views/StudentVideosView.vue'),
      meta: { requiresAuth: true, requiresRole: 'STUDENT' }
    },
  ],
})

// Navigation guard
router.beforeEach(async (to, from, next) => {
  const requiresAuth = to.meta.requiresAuth !== false
  const requiresRole = to.meta.requiresRole as string | undefined

  if (!requiresAuth) {
    // Public route
    next()
    return
  }

  try {
    // Check authentication status
    const response = await authAPI.getCurrentUser()
    const currentUser = response.data

    if (!currentUser.userId) {
      // Not authenticated
      next('/login')
      return
    }

    // Check role requirement
    if (requiresRole && currentUser.role !== requiresRole) {
      // Wrong role - redirect to appropriate dashboard
      if (currentUser.role === 'STUDENT') {
        next('/student/dashboard')
      } else {
        next('/')
      }
      return
    }

    // Authenticated and authorized
    next()
  } catch (error) {
    // Authentication failed
    next('/login')
  }
})

export default router
