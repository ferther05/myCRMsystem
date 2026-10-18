import { createApp } from 'vue'
import {
  ElAlert, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElDrawer, ElDropdown,
  ElDropdownItem, ElDropdownMenu, ElForm, ElFormItem, ElIcon, ElInput,
  ElLoading, ElMenu, ElMenuItem, ElMessage, ElPagination, ElTable, ElTableColumn, ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import '@/assets/styles/theme.css'
import '@/assets/styles/global.css'
import App from './App.vue'
import router from './router'
import { pinia } from './store'
import { useAuthStore } from './store/auth'
import { configureAuthHandlers } from './api/http'
import { TOKEN_KEY } from './utils/session'
import { safeRedirect } from './utils/auth-rules'

const app = createApp(App)
app.use(pinia)
const auth = useAuthStore(pinia)
configureAuthHandlers({
  expired() {
    const destination = safeRedirect(router.currentRoute.value.fullPath)
    auth.clear()
    if (router.currentRoute.value.path !== '/login') {
      ElMessage.warning('登录已失效，请重新登录')
      void router.replace({ path: '/login', query: { reason: 'expired', redirect: destination } })
    }
  },
  passwordRequired() {
    if (auth.user) auth.user.mustChangePassword = true
    if (router.currentRoute.value.path !== '/change-password') {
      void router.replace({ path: '/change-password', query: { redirect: safeRedirect(router.currentRoute.value.fullPath) } })
    }
  },
  forbidden() { void router.replace('/403') },
})

window.addEventListener('storage', (event) => {
  if (event.key === TOKEN_KEY || event.key === null) {
    auth.syncFromStorage()
    void router.replace({ path: '/login', query: { reason: 'changed' } })
  }
})
app.use(router)
// Register only the components used by the foundation; future modules add their own imports.
for (const component of [ElAlert, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElDrawer,
  ElDropdown, ElDropdownItem, ElDropdownMenu, ElForm, ElFormItem, ElIcon, ElInput,
  ElMenu, ElMenuItem, ElPagination, ElTable, ElTableColumn, ElTag]) {
  app.use(component)
}
app.use(ElLoading)
app.mount('#app')
