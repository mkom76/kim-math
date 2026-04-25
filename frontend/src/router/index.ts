import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
      path: '/no-academy',
      name: 'no-academy',
      component: () => import('../views/NoAcademyView.vue'),
      meta: { requiresAuth: true }
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
      path: '/tests/:testId/students/:studentId/result',
      name: 'test-student-result',
      component: () => import('../views/TestResultView.vue'),
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
      path: '/textbooks',
      name: 'textbooks',
      component: () => import('../views/TextbooksView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
    },
    {
      path: '/textbooks/:id',
      name: 'textbook-detail',
      component: () => import('../views/TextbookDetailView.vue'),
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
      path: '/admin/teachers',
      name: 'admin-teachers',
      component: () => import('../views/AdminTeachersView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER', requiresAdmin: true }
    },
    {
      path: '/admin/class-owners',
      name: 'admin-class-owners',
      component: () => import('../views/AdminClassOwnersView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER', requiresAdmin: true }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/settings/feedback-prompt',
      name: 'feedback-prompt-settings',
      component: () => import('../views/FeedbackPromptSettingsView.vue'),
      meta: { requiresAuth: true, requiresRole: 'TEACHER' }
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
      path: '/student/tests/:id/result',
      name: 'student-test-result',
      component: () => import('../views/TestResultView.vue'),
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
  const requiresAdmin = to.meta.requiresAdmin === true

  if (!requiresAuth) {
    next()
    return
  }

  const authStore = useAuthStore()

  try {
    if (!authStore.userId) {
      await authStore.loadCurrentUser()
    }
    if (!authStore.userId) {
      next('/login')
      return
    }

    if (authStore.role === 'TEACHER') {
      if (authStore.memberships.length === 0) {
        if (to.name !== 'no-academy') {
          next('/no-academy')
          return
        }
      } else {
        await authStore.ensureActiveAcademy()
      }
    }

    if (requiresRole && authStore.role !== requiresRole) {
      next(authStore.role === 'STUDENT' ? '/student/dashboard' : '/')
      return
    }

    if (requiresAdmin && !authStore.isAdmin) {
      next('/')
      return
    }

    next()
  } catch (error) {
    next('/login')
  }
})

export default router
