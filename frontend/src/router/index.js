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
  // §4 投递模块：候选人端
  { path: '/applications/mine', name: 'my-applications', component: () => import('@/views/candidate/MyApplications.vue'), meta: { roles: ['CANDIDATE'] } },
  { path: '/applications/:id', name: 'application-detail', component: () => import('@/views/candidate/ApplicationDetail.vue'), meta: { roles: ['CANDIDATE', 'HR', 'ADMIN'] } },
  // §4 投递模块：HR 端
  { path: '/hr/applications', name: 'hr-applications', component: () => import('@/views/hr/ApplicationInbox.vue'), meta: { roles: ['HR'] } },
  { path: '/hr/applications/:id', name: 'hr-application-detail', component: () => import('@/views/hr/ApplicationDetail.vue'), meta: { roles: ['HR'] } },
  { path: '/hr/jobs', name: 'hr-jobs', component: () => import('@/views/hr/JobManage.vue'), meta: { roles: ['HR'] } },
  // §9 管理员审核（v0.4：UC-35 职位审核删除）
  { path: '/admin/users/audit', name: 'admin-users-audit', component: () => import('@/views/admin/UserAudit.vue'), meta: { roles: ['ADMIN'] } },
  { path: '/admin/companies/audit', name: 'admin-companies-audit', component: () => import('@/views/admin/CompanyAudit.vue'), meta: { roles: ['ADMIN'] } },
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
