/* 文件作用：初始化 Vue、组件、路由及全局认证处理。 */
import { createApp } from 'vue';
import {
  ElAlert,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElLoading,
  ElMenu,
  ElMenuItem,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import 'element-plus/dist/index.css';
import '@/assets/styles/theme.css';
import '@/assets/styles/global.css';
import App from './App.vue';
import router from './router';
import { pinia } from './store';
import { useAuthStore } from './store/auth';
import { configureAuthHandlers } from './api/http';
import { TOKEN_KEY } from './utils/session';
import { safeRedirect } from './utils/auth-rules';

const app = createApp(App);
app.use(pinia);
const auth = useAuthStore(pinia);
configureAuthHandlers({
  /** 清理失效会话并返回登录页。 */
  expired() {
    const destination = safeRedirect(router.currentRoute.value.fullPath);
    auth.clear();
    if (router.currentRoute.value.path !== '/login') {
      ElMessage.warning('登录已失效，请重新登录');
      void router.replace({ path: '/login', query: { reason: 'expired', redirect: destination } });
    }
  },
  /** 转到首次修改密码页面。 */
  passwordRequired() {
    if (auth.user) auth.user.mustChangePassword = true;
    if (router.currentRoute.value.path !== '/change-password') {
      void router.replace({
        path: '/change-password',
        query: { redirect: safeRedirect(router.currentRoute.value.fullPath) },
      });
    }
  },
  /** 转到无权限提示页面。 */
  forbidden() {
    void router.replace('/403');
  },
});

window.addEventListener(
  'storage',
  /* 响应其他标签页的令牌变化并同步退出旧身份。 */ (event) => {
    if (event.key === TOKEN_KEY || event.key === null) {
      auth.syncFromStorage();
      void router.replace({ path: '/login', query: { reason: 'changed' } });
    }
  },
);
app.use(router);
// 集中注册当前使用的基础组件；业务模块按需导入其他组件。
for (const component of [
  ElAlert,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMenu,
  ElMenuItem,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
]) {
  app.use(component);
}
app.use(ElLoading);
app.mount('#app');
