import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/useAuthStore'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/auth/Login.vue'), meta: { standalone: true } },
  { path: '/register', name: 'register', component: () => import('@/views/auth/Register.vue'), meta: { standalone: true } },
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/profile', name: 'profile', component: () => import('@/views/common/Profile.vue') },
  { path: '/resume', name: 'resume', component: () => import('@/views/candidate/ResumeEdit.vue'), meta: { roles: ['CANDIDATE'] } },
  { path: '/jobs', name: 'jobs', component: () => import('@/views/candidate/JobBrowse.vue') },
  { path: '/jobs/:id', name: 'job-detail', component: () => import('@/views/candidate/JobDetail.vue') },
  { path: '/hr/jobs', name: 'hr-jobs', component: () => import('@/views/hr/JobManage.vue'), meta: { roles: ['HR'] } },
  { path: '/forbidden', name: 'forbidden', component: () => import('@/views/common/Forbidden.vue') },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/common/NotFound.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.bootstrapFromToken()
  if (to.meta.standalone) {
    return true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const allowed = to.meta.roles
  if (Array.isArray(allowed) && allowed.length > 0 && !allowed.includes(auth.role)) {
    return { name: 'forbidden' }
  }
  return true
})

export default router
