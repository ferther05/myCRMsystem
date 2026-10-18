<script setup>
import { computed, reactive, ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, User, Lock, Connection, Document, TrendCharts } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAuthStore } from '@/store/auth'
import { safeRedirect, validLoginPassword } from '@/utils/auth-rules'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const failure = ref('')
const retrying = ref(false)
const notice = computed(() => ({
  expired: '登录已失效，请重新登录。', changed: '账号会话已在其他页面变更，请重新确认登录。',
  'password-changed': '密码修改成功，请使用新密码重新登录。',
  unavailable: '暂时无法确认登录状态，请重试连接或重新登录。',
}[route.query.reason] || ''))
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }, { max: 64, message: '账号最多 64 个字符', trigger: 'blur' }],
  password: [{ validator: (_rule, value, callback) => callback(validLoginPassword(value) ? undefined : new Error('请输入有效密码，长度不超过 72 字节')), trigger: 'blur' }],
}
async function submit() {
  if (submitting.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  failure.value = ''
  submitting.value = true
  try {
    const user = await auth.signIn({ username: form.username, password: form.password })
    const redirect = safeRedirect(route.query.redirect)
    await router.replace(user.mustChangePassword ? { path: '/change-password', query: { redirect } } : redirect)
  } catch (error) {
    failure.value = error.message
    if (error.code === 40101) form.password = ''
  } finally { submitting.value = false }
}
async function retrySession() {
  retrying.value = true
  failure.value = ''
  try {
    const user = await auth.ensureUser(true)
    if (user) await router.replace(user.mustChangePassword ? '/change-password' : safeRedirect(route.query.redirect))
    else failure.value = '请重新输入账号和密码'
  } catch (error) { failure.value = error.message }
  finally { retrying.value = false }
}
onBeforeUnmount(() => { form.password = '' })
</script>

<template>
  <main class="auth-screen login-screen">
    <section class="login-intro" aria-label="CRM 客户关系管理">
      <BrandMark light />
      <div class="intro-content">
        <span class="intro-kicker">从建立联系，到持续合作</span>
        <h1>让每一次跟进，<br />都有下一步。</h1>
        <p>连接客户、商机与团队协作，<br />让销售过程更清晰，让客户关系更长久。</p>
        <div class="journey" aria-hidden="true">
          <div class="journey-line" />
          <div class="journey-item"><span><el-icon><Connection /></el-icon></span><div><small>01 / 建立联系</small><strong>把客户放在心上</strong></div></div>
          <div class="journey-item"><span><el-icon><TrendCharts /></el-icon></span><div><small>02 / 把握机会</small><strong>让每一步推进有迹可循</strong></div></div>
          <div class="journey-item"><span><el-icon><Document /></el-icon></span><div><small>03 / 持续合作</small><strong>从一次成交，到长期信任</strong></div></div>
        </div>
      </div>
      <div class="intro-bottom"><span class="intro-dot" /> 专注客户关系，协同每一次成长</div>
    </section>

    <section class="login-main">
      <div class="mobile-brand"><BrandMark /></div>
      <div class="login-form-wrap">
        <span class="eyebrow">团队工作空间</span>
        <h2>欢迎回来</h2>
        <p class="login-subtitle">登录账号，开始今天的工作。</p>
        <el-alert v-if="notice" :title="notice" :type="route.query.reason === 'password-changed' ? 'success' : 'info'" :closable="false" show-icon class="form-error" />
        <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon class="form-error" role="alert" />
        <el-button v-if="route.query.reason === 'unavailable' && auth.token" class="retry-session" :loading="retrying" @click="retrySession">重新连接已有会话</el-button>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
          <el-form-item label="账号" prop="username" for="login-username"><el-input id="login-username" v-model="form.username" autocomplete="username" placeholder="请输入账号" :prefix-icon="User" maxlength="64" :disabled="submitting" /></el-form-item>
          <el-form-item label="密码" prop="password" for="login-password"><el-input id="login-password" v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" :prefix-icon="Lock" maxlength="72" :disabled="submitting" /></el-form-item>
          <el-button type="primary" native-type="submit" class="full-width login-submit" :loading="submitting">{{ submitting ? '正在登录' : '登录工作空间' }}<el-icon v-if="!submitting" class="submit-arrow"><ArrowRight /></el-icon></el-button>
        </el-form>
        <div class="login-help"><el-icon><Lock /></el-icon><span>账号由管理员统一开通，首次登录需修改密码。</span></div>
      </div>
      <footer class="auth-footer">CRM 客户关系管理系统 <span>·</span> 团队协作，从这里开始</footer>
    </section>
  </main>
</template>

<style scoped>
.login-screen { display: grid; grid-template-columns: minmax(400px, 44%) 1fr; background: #fff; }
.login-intro { position: relative; display: flex; flex-direction: column; justify-content: space-between; min-height: 100vh; padding: 46px clamp(36px, 4.5vw, 80px) 32px; background: var(--crm-sidebar); color: white; overflow: hidden; }
.login-intro::after { content: ''; position: absolute; width: 340px; height: 340px; border: 1px solid #334760; border-radius: 50%; right: -200px; top: 18%; pointer-events: none; }
.intro-content { padding: 52px 0 44px; z-index: 1; }
.intro-kicker { color: #a5bdde; font-size: 12px; letter-spacing: 2px; }
.intro-content h1 { font-size: clamp(32px, 3vw, 44px); line-height: 1.55; letter-spacing: -1px; margin: 16px 0 22px; font-weight: 600; }
.intro-content p { color: #b5c6dd; font-size: 14px; line-height: 1.9; }
.journey { position: relative; margin-top: 40px; display: grid; gap: 27px; }
.journey-line { position: absolute; top: 24px; bottom: 25px; left: 21px; width: 1px; background: #3c526e; }
.journey-item { display: flex; align-items: center; gap: 16px; z-index: 1; }
.journey-item > span { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 10px; background: #203b5e; border: 1px solid #3a5575; font-size: 19px; color: #aac9ff; }
.journey-item small { display: block; font-size: 10px; color: #a5bdde; letter-spacing: 1px; margin-bottom: 4px; }
.journey-item strong { font-size: 13px; font-weight: 500; color: #e4edf9; }
.intro-bottom { color: #a7bdd9; font-size: 11px; letter-spacing: .5px; }
.intro-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #7ac6af; margin-right: 7px; }
.login-main { display: flex; min-height: 100vh; align-items: center; justify-content: center; position: relative; padding: 80px 32px; }
.login-form-wrap { width: 100%; max-width: 380px; }
.login-form-wrap h2 { font-size: 29px; margin-bottom: 10px; font-weight: 650; letter-spacing: -.5px; }
.login-subtitle { margin-bottom: 34px; color: var(--crm-text-secondary); }
.login-help { display: flex; align-items: center; justify-content: center; gap: 7px; color: var(--crm-text-secondary); font-size: 11px; padding-top: 25px; margin-top: 25px; border-top: 1px solid #e7ebf2; }
.login-submit { height: 47px; margin-top: 6px; font-size: 14px; }
.submit-arrow { margin-left: 12px; }
.auth-footer { position: absolute; bottom: 26px; left: 20px; right: 20px; text-align: center; font-size: 11px; }
.auth-footer span { padding: 0 8px; }
.mobile-brand { display: none; }
.retry-session { width: 100%; margin-bottom: 20px; }
@media (max-width: 850px) {
  .login-screen { display: block; background: var(--crm-bg-page); }
  .login-intro { display: none; }
  .login-main { min-height: 100dvh; flex-direction: column; padding: 36px 24px 74px; gap: 38px; }
  .mobile-brand { display: block; width: 100%; max-width: 380px; }
  .login-form-wrap { padding: 32px 26px; background: white; border: 1px solid #e3e9f1; border-radius: 12px; }
  .login-help { font-size: 11px; line-height: 1.7; align-items: flex-start; }
}
</style>
