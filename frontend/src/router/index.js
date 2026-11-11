/* 文件作用：配置页面路由、懒加载、登录守卫与角色访问限制。 */
import { createRouter, createWebHistory } from 'vue-router';
import { pinia } from '@/store';
import { useAuthStore } from '@/store/auth';
import { safeRedirect } from '@/utils/auth-rules';
import { modules, canVisit } from './modules';

const moduleViews = {
  /** 按需加载管理员回收预览。 */
  '/recycle': () => import('@/views/customer/RecycleView.vue'),
  /** 按需加载任务与日志页面。 */
  '/tasks': () => import('@/views/task/TasksView.vue'),
  /** 按需加载业务页面组件。 */
  '/workbench': () => import('@/views/workbench/WorkbenchView.vue'),
  /** 按需加载业务页面组件。 */
  '/team': () => import('@/views/account/UsersView.vue'),
  /** 按需加载业务页面组件。 */
  '/system': () => import('@/views/account/UsersView.vue'),
  /** 按需加载业务页面组件。 */
  '/approvals': () => import('@/views/account/ApplicationsView.vue'),
  /** 按需加载业务页面组件。 */
  '/customers': () => import('@/views/customer/CustomersView.vue'),
  /** 按需加载业务页面组件。 */
  '/public-pool': () => import('@/views/customer/CustomersView.vue'),
  /** 按需加载业务页面组件。 */
  '/opportunities': () => import('@/views/opportunity/OpportunitiesView.vue'),
  /** 按需加载业务页面组件。 */
  '/analytics': () => import('@/views/analytics/AnalyticsView.vue'),
};

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      /* 按需加载目标页面组件。 */ component: () => import('@/views/auth/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/change-password',
      name: 'change-password',
      /* 按需加载目标页面组件。 */ component: () => import('@/views/auth/ChangePasswordView.vue'),
      meta: { title: '修改密码' },
    },
    {
      path: '/',
      /* 按需加载目标页面组件。 */ component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/workbench' },
        // 兼容旧收藏、看板和待办中的合同链接，统一进入商机管理。
        { path: 'contracts', redirect: (to) => {
          const { id, ...query } = to.query;
          return { path: '/opportunities', query: { ...query, view: 'contracts', ...(id ? { contractId: id } : {}) } };
        } },
        ...modules.map(
          /* 按需加载业务页面组件。 */ (item) => ({
            path: item.path.slice(1),
            component:
              moduleViews[item.path] ||
              /* 按需加载业务页面组件。 */ (() => import('@/views/common/ModulePendingView.vue')),
            meta: item,
          }),
        ),
        {
          path: 'account',
          /* 按需加载目标页面组件。 */ component: () => import('@/views/account/AccountView.vue'),
          meta: { title: '个人中心' },
        },
        {
          path: '403',
          /* 按需加载目标页面组件。 */ component: () => import('@/views/common/StatusView.vue'),
          props: { code: '403' },
          meta: { title: '无访问权限' },
        },
        {
          path: ':pathMatch(.*)*',
          /* 按需加载目标页面组件。 */ component: () => import('@/views/common/StatusView.vue'),
          props: { code: '404' },
          meta: { title: '页面不存在' },
        },
      ],
    },
  ],
  /** 切换页面后将滚动位置回到顶部。 */
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(
  /* 进入页面前核对登录身份、首次改密与角色权限。 */ async (to) => {
    const auth = useAuthStore(pinia);
    if (to.meta.public) return true;
    if (!auth.token && !localStorage.getItem('crm.access-token')) {
      return { path: '/login', query: { redirect: safeRedirect(to.fullPath) } };
    }
    try {
      // 页面导航时核对服务端最新身份，不直接信任本地令牌中的旧角色。
      const user = await auth.ensureUser(true);
      if (!user) return { path: '/login', query: { redirect: safeRedirect(to.fullPath) } };
      if (user.mustChangePassword && to.name !== 'change-password') {
        return { path: '/change-password', query: { redirect: safeRedirect(to.fullPath) } };
      }
      if (to.path === '/team' && user.roleCode === 'ADMIN') return '/system';
      if (!canVisit(to.meta, user.roleCode)) return '/403';
      return true;
    } catch (error) {
      if (error.status === 401)
        return {
          path: '/login',
          query: { reason: 'expired', redirect: safeRedirect(to.fullPath) },
        };
      // 网络或服务暂时故障时展示重试页面，保留本地令牌供恢复使用。
      return {
        path: '/login',
        query: { reason: 'unavailable', redirect: safeRedirect(to.fullPath) },
      };
    }
  },
);

router.afterEach(
  /* 导航完成后更新浏览器页面标题。 */ (to) => {
    document.title = `${to.meta.title || '客户管理'} · CRM`;
  },
);
export default router;
