<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Fold, Expand, ArrowDown, Lock, SwitchButton, User } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import AppNavigation from './AppNavigation.vue'
import { useAuthStore } from '@/store/auth'
import { ROLE_NAMES } from '@/utils/auth-rules'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const compact = ref(false)
const drawer = ref(false)
const leaving = ref(false)
const initial = computed(() => auth.user?.realName?.slice(0, 1) || '我')
async function command(value) {
  if (value === 'account') return router.push('/account')
  if (value === 'password') return router.push({ path: '/change-password', query: { redirect: route.fullPath } })
  try { await ElMessageBox.confirm('退出后需要重新登录才能访问工作空间。', '退出当前账号', { confirmButtonText: '确认退出', cancelButtonText: '取消', type: 'info' }) }
  catch { return }
  leaving.value = true
  try { await auth.signOut(); await router.replace('/login') }
  catch (error) { if (error.status !== 401) ElMessage.error(error.message) }
  finally { leaving.value = false }
}
</script>
<template>
  <div class="app-shell" :class="{ compact }">
    <a class="skip-link" href="#main-content">跳至主要内容</a>
    <aside class="app-sidebar">
      <router-link class="sidebar-brand" to="/workbench" aria-label="CRM 工作台"><BrandMark light :compact="compact" /></router-link>
      <div class="sidebar-scroll"><AppNavigation :compact="compact" /></div>
      <div v-if="!compact" class="sidebar-footer"><span class="workspace-dot" /><div><strong>{{ auth.user?.teamName || '系统管理工作空间' }}</strong><small>{{ ROLE_NAMES[auth.user?.roleCode] }}</small></div></div>
    </aside>
    <div class="app-body">
      <header class="app-header">
        <div class="header-left">
          <el-button text circle class="desktop-toggle" :aria-label="compact ? '展开侧栏' : '收起侧栏'" @click="compact = !compact"><el-icon :size="20"><Expand v-if="compact" /><Fold v-else /></el-icon></el-button>
          <el-button text circle class="mobile-toggle" aria-label="打开主导航" @click="drawer = true"><el-icon :size="20"><Expand /></el-icon></el-button>
          <el-breadcrumb separator="/"><el-breadcrumb-item>工作空间</el-breadcrumb-item><el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item></el-breadcrumb>
        </div>
        <el-dropdown trigger="click" @command="command">
          <button class="user-trigger" :disabled="leaving" aria-label="打开账号菜单"><span class="user-avatar">{{ initial }}</span><span class="user-name">{{ auth.user?.realName }}<small>{{ ROLE_NAMES[auth.user?.roleCode] }}</small></span><el-icon><ArrowDown /></el-icon></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="account" :icon="User">个人中心</el-dropdown-item><el-dropdown-item command="password" :icon="Lock">修改密码</el-dropdown-item><el-dropdown-item command="logout" divided :icon="SwitchButton">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </header>
      <main id="main-content" class="main-content" tabindex="-1"><router-view /></main>
      <footer class="app-footer">CRM 客户关系管理系统 <span>让协作更顺畅，让关系更长久</span></footer>
    </div>
    <el-drawer v-model="drawer" direction="ltr" size="270px" class="nav-drawer" title="主导航">
      <template #header><BrandMark light /></template><AppNavigation @navigate="drawer = false" />
    </el-drawer>
  </div>
</template>
<style scoped>
.app-shell { --sidebar-width: 232px; min-height: 100vh; }.app-shell.compact { --sidebar-width: 76px; }
.app-sidebar { position: fixed; inset: 0 auto 0 0; width: var(--sidebar-width); display: flex; flex-direction: column; background: var(--crm-sidebar); z-index: 50; }
.sidebar-brand { display: block; padding: 23px 24px; height: 92px; text-decoration: none; }.compact .sidebar-brand { padding: 23px 19px; }
.sidebar-scroll { overflow-y: auto; scrollbar-width: thin; scrollbar-color: #425975 transparent; flex: 1; padding-bottom: 15px; }
.sidebar-footer { display: flex; gap: 10px; padding: 20px; border-top: 1px solid #2c3e58; color: #d1dced; }.sidebar-footer strong { display: block; font-size: 11px; font-weight: 500; }.sidebar-footer small { display: block; margin-top: 2px; color: #a4b8d2; font-size: 10px; }.workspace-dot { width: 7px; height: 7px; background: #69bda0; border-radius: 50%; margin-top: 7px; flex-shrink: 0; }
.app-body { margin-left: var(--sidebar-width); min-height: 100vh; display: flex; flex-direction: column; }
.app-header { height: 72px; padding: 0 30px; border-bottom: 1px solid #e3e9f1; background: white; display: flex; justify-content: space-between; align-items: center; gap: 14px; }
.header-left { display: flex; align-items: center; gap: 17px; }.el-breadcrumb { font-size: 12px; }.mobile-toggle { display: none; }
.user-trigger { display: flex; align-items: center; gap: 11px; border: 0; background: transparent; cursor: pointer; color: var(--crm-text-main); padding: 4px; border-radius: 6px; }.user-avatar { background: #eaf0fe; color: #245ee5; border: 1px solid #d7e3ff; height: 34px; width: 34px; border-radius: 50%; display: grid; place-items: center; font-size: 14px; }.user-name { text-align: left; font-size: 12px; font-weight: 600; }.user-name small { display: block; font-size: 10px; color: var(--crm-text-secondary); font-weight: 400; }
.main-content { flex: 1; padding: 30px; width: 100%; max-width: 1550px; margin: 0 auto; outline: none; }
.main-content:focus-visible { outline: 2px solid #87b7ff; outline-offset: -4px; }
.app-footer { display: flex; justify-content: space-between; padding: 17px 30px; border-top: 1px solid #e0e6ef; font-size: 10px; color: var(--crm-text-secondary); }
@media (max-width: 1000px) { .app-header { padding: 0 20px; }.main-content { padding: 24px; } }
@media (max-width: 760px) { .app-sidebar { display: none; }.app-body { margin-left: 0; }.desktop-toggle { display: none; }.mobile-toggle { display: inline-flex; }.app-header { height: 64px; padding: 0 16px; }.header-left { gap: 10px; }.main-content { padding: 22px 18px; }.user-name { display: none; }.app-footer { padding: 16px 18px; }.app-footer span { display: none; } }
</style>
<style>.nav-drawer { background: var(--crm-sidebar) !important; }.nav-drawer .el-drawer__header { margin: 0; padding: 25px 22px; }.nav-drawer .el-drawer__body { padding: 0; }.nav-drawer .el-drawer__close-btn { color: #c0cde0; }</style>
