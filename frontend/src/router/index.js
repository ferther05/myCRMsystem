import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'
import { safeRedirect } from '@/utils/auth-rules'
import { modules, canVisit } from './modules'

const moduleViews = {
  '/workbench': () => import('@/views/workbench/WorkbenchView.vue'),
  '/team': () => import('@/views/account/UsersView.vue'),
  '/system': () => import('@/views/account/UsersView.vue'),
  '/approvals': () => import('@/views/account/ApplicationsView.vue'),
  '/customers': () => import('@/views/customer/CustomersView.vue'),
  '/public-pool': () => import('@/views/customer/CustomersView.vue'),
  '/opportunities': () => import('@/views/opportunity/OpportunitiesView.vue'),
  '/analytics': () => import('@/views/analytics/AnalyticsView.vue'),
  '/contracts': () => import('@/views/contract/ContractsView.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/change-password', name: 'change-password', component: () => import('@/views/auth/ChangePasswordView.vue'), meta: { title: '修改密码' } },
    { path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
      { path: '', redirect: '/workbench' },
      ...modules.map((item) => ({
        path: item.path.slice(1), component: moduleViews[item.path] || (() => import('@/views/common/ModulePendingView.vue')),
        meta: item,
      })),
      { path: 'account', component: () => import('@/views/account/AccountView.vue'), meta: { title: '个人中心' } },
      { path: '403', component: () => import('@/views/common/StatusView.vue'), props: { code: '403' }, meta: { title: '无访问权限' } },
      { path: ':pathMatch(.*)*', component: () => import('@/views/common/StatusView.vue'), props: { code: '404' }, meta: { title: '页面不存在' } },
    ] },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.public) return true
  if (!auth.token && !localStorage.getItem('crm.access-token')) {
    return { path: '/login', query: { redirect: safeRedirect(to.fullPath) } }
  }
  try {
    // Check current server identity on navigation, rather than trusting stored JWT role claims.
    const user = await auth.ensureUser(true)
    if (!user) return { path: '/login', query: { redirect: safeRedirect(to.fullPath) } }
    if (user.mustChangePassword && to.name !== 'change-password') {
      return { path: '/change-password', query: { redirect: safeRedirect(to.fullPath) } }
    }
    if (to.path === '/team' && user.roleCode === 'ADMIN') return '/system'
    if (!canVisit(to.meta, user.roleCode)) return '/403'
    return true
  } catch (error) {
    if (error.status === 401) return { path: '/login', query: { reason: 'expired', redirect: safeRedirect(to.fullPath) } }
    // Temporary network/service errors show a retry screen; a valid local token is preserved.
    return { path: '/login', query: { reason: 'unavailable', redirect: safeRedirect(to.fullPath) } }
  }
})

router.afterEach((to) => { document.title = `${to.meta.title || '客户管理'} · CRM` })
export default router
