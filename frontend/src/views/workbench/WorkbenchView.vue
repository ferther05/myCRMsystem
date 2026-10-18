<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh, Lock, CircleCheck, Clock } from '@element-plus/icons-vue'
import PageHeading from '@/components/PageHeading.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/store/auth'
import { ROLE_NAMES } from '@/utils/auth-rules'
import { modules, canVisit } from '@/router/modules'
import { getHealth } from '@/api/health'

const auth = useAuthStore()
const router = useRouter()
const health = ref(null)
const loading = ref(true)
const failure = ref('')
const date = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const spaces = computed(() => modules.filter((item) => item.path !== '/workbench' && canVisit(item, auth.user?.roleCode)))
const greeting = computed(() => {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: 'numeric', hour12: false }).format(new Date()))
  return hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
})
async function checkHealth() {
  loading.value = true
  failure.value = ''
  try { health.value = await getHealth() }
  catch (error) { health.value = null; failure.value = error.message }
  finally { loading.value = false }
}
onMounted(checkHealth)
</script>
<template>
  <div>
    <PageHeading title="工作台" description="你的工作空间，今天也从清晰有序开始。" eyebrow="WORKSPACE"><span class="date-label"><el-icon><Clock /></el-icon>{{ date }}</span></PageHeading>
    <section class="welcome-panel panel">
      <div><span class="welcome-label">欢迎进入 CRM</span><h2>{{ greeting }}，{{ auth.user?.realName }}</h2><p>把客户关系串联起来，让每一次协作都有方向。</p><el-button type="primary" @click="router.push('/account')">查看个人中心<el-icon class="button-arrow"><ArrowRight /></el-icon></el-button></div>
      <div class="welcome-art" aria-hidden="true"><div class="art-ring ring-one" /><div class="art-ring ring-two" /><div class="art-center">CRM</div><div class="art-note note-one"><el-icon><CircleCheck /></el-icon> 清晰跟进</div><div class="art-note note-two"><el-icon><CircleCheck /></el-icon> 团队协同</div></div>
    </section>
    <section class="account-strip panel" aria-label="当前账号信息"><div><span>当前身份</span><strong>{{ ROLE_NAMES[auth.user?.roleCode] }}</strong></div><div><span>所属团队</span><strong>{{ auth.user?.teamName || '系统管理工作空间' }}</strong></div><div><span>账号安全</span><strong class="safe-status"><el-icon><CircleCheck /></el-icon> 已完成密码设置</strong></div></section>
    <div class="workspace-columns">
      <section>
        <div class="section-heading"><h2>业务工作区</h2><span>按你的角色展示可用入口</span></div>
        <div class="module-grid"><router-link v-for="item in spaces" :key="item.path" :to="item.path" class="module-card panel"><span class="module-icon"><AppIcon :name="item.icon" /></span><div><h3>{{ item.title }}<span class="pending-label">{{ item.ready ? '已开放' : '待开发' }}</span></h3><p>{{ item.description }}</p></div><el-icon class="module-arrow"><ArrowRight /></el-icon></router-link></div>
      </section>
      <aside class="workbench-aside">
        <section class="panel connection-panel"><div class="section-heading"><h2>工作空间连接</h2><el-button text circle :icon="Refresh" aria-label="刷新连接状态" :loading="loading" @click="checkHealth" /></div><div v-if="loading" class="connection-status muted">正在检查连接…</div><template v-else-if="health?.status === 'UP'"><div class="connection-status"><span class="connection-dot" /><strong>连接正常</strong></div><p>工作空间已连接，可以继续操作。</p></template><template v-else><div class="connection-status connection-error" role="alert">暂时无法连接</div><p>{{ failure || '请稍后重新检查连接。' }}</p><el-button size="small" @click="checkHealth">重试</el-button></template></section>
        <section class="panel security-panel"><div class="security-title"><el-icon><Lock /></el-icon><h2>账号安全</h2></div><p>保护好你的登录密码。密码修改后，需要重新登录。</p><router-link to="/change-password">修改登录密码 <el-icon><ArrowRight /></el-icon></router-link></section>
        <section class="workspace-note"><span class="eyebrow">工作空间正在完善</span><p>客户、商机、合同与回款等业务将逐步开放，届时这里会展示你的真实待办与业务概况。</p></section>
      </aside>
    </div>
  </div>
</template>
<style scoped>
.date-label { display: flex; align-items: center; gap: 7px; font-size: 12px; color: var(--crm-text-secondary); margin-top: 29px; }
.welcome-panel { min-height: 218px; display: flex; align-items: center; justify-content: space-between; padding: 30px 36px; background: #edf2ff; border-color: #d9e4ff; overflow: hidden; }
.welcome-label { display: block; font-size: 11px; letter-spacing: 1px; color: #3f5f95; margin-bottom: 8px; }.welcome-panel h2 { font-size: 25px; margin-bottom: 9px; font-weight: 600; }.welcome-panel p { color: #4c6385; font-size: 13px; margin-bottom: 22px; }.button-arrow { margin-left: 9px; }
.welcome-art { position: relative; width: 250px; height: 170px; flex-shrink: 0; margin-left: 24px; }.art-ring { position: absolute; width: 156px; height: 156px; border: 1px solid #c9d8f5; border-radius: 50%; left: 48px; top: 7px; }.ring-two { width: 115px; height: 115px; left: 69px; top: 28px; border-style: dashed; }.art-center { position: absolute; left: 91px; top: 50px; height: 72px; width: 72px; display: grid; place-items: center; border-radius: 18px; background: #245ee5; color: white; font-size: 20px; letter-spacing: 1px; box-shadow: 0 10px 28px #245ee52b; }.art-note { position: absolute; padding: 8px 12px; display: flex; align-items: center; gap: 7px; background: white; border: 1px solid #d9e4f6; border-radius: 6px; font-size: 11px; color: #365680; }.art-note .el-icon { color: #167753; }.note-one { left: 3px; top: 17px; }.note-two { right: 0; bottom: 9px; }
.account-strip { display: grid; grid-template-columns: 1fr 1.3fr 1fr; padding: 22px 26px; margin: 20px 0 30px; }.account-strip > div { padding: 0 20px; border-right: 1px solid #e4e9f1; }.account-strip > div:first-child { padding-left: 0; }.account-strip > div:last-child { border: 0; }.account-strip span { display: block; font-size: 11px; color: var(--crm-text-secondary); margin-bottom: 5px; }.account-strip strong { font-size: 13px; font-weight: 550; }.safe-status { display: flex; align-items: center; gap: 6px; color: #167753; }
.workspace-columns { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 24px; }.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }.section-heading h2 { font-size: 15px; margin: 0; }.section-heading > span { font-size: 11px; color: var(--crm-text-secondary); }.module-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.module-card { display: flex; align-items: center; gap: 12px; padding: 21px 18px; text-decoration: none; color: inherit; }.module-card:hover { border-color: #9fb9f2; box-shadow: 0 3px 14px #1d355b09; }.module-icon { width: 39px; height: 39px; display: grid; place-items: center; border-radius: 8px; background: #f0f4fb; color: #3763a5; flex-shrink: 0; font-size: 20px; }.module-card h3 { display: flex; align-items: center; gap: 8px; font-size: 13px; margin-bottom: 5px; }.module-card p { font-size: 10px; color: var(--crm-text-secondary); margin: 0; }.pending-label { color: #64758b; font-size: 9px; font-weight: 400; border: 1px solid #dce3ed; padding: 0 4px; border-radius: 3px; white-space: nowrap; }.module-arrow { margin-left: auto; color: #74849a; font-size: 12px; }
.connection-panel, .security-panel { padding: 22px; margin-bottom: 16px; }.connection-panel .section-heading { margin-bottom: 12px; }.connection-status { display: flex; align-items: center; gap: 8px; font-size: 13px; }.connection-dot { width: 7px; height: 7px; border-radius: 50%; background: #167753; }.connection-panel p, .security-panel p { color: var(--crm-text-secondary); font-size: 12px; line-height: 1.8; margin-top: 10px; margin-bottom: 0; }.connection-error { color: var(--crm-color-danger); }.connection-panel .el-button:not(.is-circle) { margin-top: 10px; }.security-title { display: flex; gap: 8px; align-items: center; }.security-title .el-icon { color: var(--crm-color-brand); }.security-title h2 { font-size: 14px; margin: 0; }.security-panel a { display: inline-flex; align-items: center; gap: 7px; font-size: 12px; margin-top: 16px; }.workspace-note { padding: 8px 10px; }.workspace-note .eyebrow { font-size: 10px; letter-spacing: .5px; margin-bottom: 7px; }.workspace-note p { font-size: 11px; color: var(--crm-text-secondary); line-height: 1.8; }
@media (max-width: 1200px) { .workspace-columns { grid-template-columns: 1fr; }.workbench-aside { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.workspace-note { grid-column: 1 / -1; }.module-card { padding: 20px; }.connection-panel, .security-panel { margin: 0; } }
@media (max-width: 850px) { .welcome-art { display: none; } }
@media (max-width: 600px) { .welcome-panel { padding: 26px 22px; }.welcome-panel h2 { font-size: 23px; }.account-strip { grid-template-columns: 1fr; padding: 6px 22px; }.account-strip > div { border-right: 0; border-bottom: 1px solid #e4e9f1; padding: 12px 0; }.module-grid, .workbench-aside { grid-template-columns: 1fr; }.workspace-note { grid-column: auto; }.date-label { margin-top: 0; }.section-heading { align-items: flex-start; }.section-heading > span { font-size: 10px; }.module-card p { font-size: 11px; } }
</style>
