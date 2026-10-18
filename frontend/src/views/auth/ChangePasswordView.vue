<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, CircleCheck, ArrowLeft } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAuthStore } from '@/store/auth'
import { changePassword } from '@/api/auth'
import { safeRedirect, validNewPassword, validLoginPassword } from '@/utils/auth-rules'
import { readToken } from '@/utils/session'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mandatory = computed(() => auth.user?.mustChangePassword)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const formRef = ref()
const submitting = ref(false)
const leaving = ref(false)
const failure = ref('')
const rules = {
  oldPassword: [{ validator: (_r, value, cb) => cb(validLoginPassword(value) ? undefined : new Error('请输入当前密码')), trigger: 'blur' }],
  newPassword: [{ validator: (_r, value, cb) => {
    if (!validNewPassword(value)) return cb(new Error('请输入 8～64 位密码，至少包含英文字母和数字'))
    cb(value === form.oldPassword ? new Error('新密码不能与原密码相同') : undefined)
  }, trigger: 'blur' }],
  confirmPassword: [{ validator: (_r, value, cb) => cb(value && value === form.newPassword ? undefined : new Error('两次输入的新密码不一致')), trigger: 'blur' }],
}
async function submit() {
  if (submitting.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  failure.value = ''
  submitting.value = true
  const current = readToken()
  try {
    await changePassword({ ...form })
    auth.clear(current)
    ElMessage.success('密码已修改，请使用新密码登录')
    await router.replace({ path: '/login', query: { reason: 'password-changed', redirect: safeRedirect(route.query.redirect) } })
  } catch (error) { failure.value = error.message }
  finally { submitting.value = false }
}
async function leave() {
  leaving.value = true
  failure.value = ''
  try { await auth.signOut(); await router.replace('/login') }
  catch (error) { failure.value = error.message }
  finally { leaving.value = false }
}
onBeforeUnmount(() => { form.oldPassword = ''; form.newPassword = ''; form.confirmPassword = '' })
</script>
<template>
  <div class="auth-screen password-screen">
    <header class="password-header"><BrandMark /><span class="muted">账号安全</span></header>
    <main class="password-main">
      <div class="password-heading"><span class="eyebrow">保护你的工作空间</span><h1>{{ mandatory ? '首次登录，先设置新密码' : '修改登录密码' }}</h1><p class="muted">{{ mandatory ? '完成这一步后，即可开始使用工作空间。' : '定期更新密码，让账号更安心。' }}</p></div>
      <div class="panel password-card">
        <aside class="password-guide"><div class="guide-icon"><el-icon><Lock /></el-icon></div><h2>{{ auth.user?.realName || '你好' }}，你好</h2><p>请为你的账号设置一个专用密码，妥善保管，避免与他人共享。</p><div class="guide-rule"><el-icon><CircleCheck /></el-icon><span>8～64 位，包含英文字母和数字</span></div><div class="guide-rule"><el-icon><CircleCheck /></el-icon><span>可使用英文标点，不含空格或中文</span></div><div class="guide-rule"><el-icon><CircleCheck /></el-icon><span>修改后需使用新密码重新登录</span></div></aside>
        <section class="password-form">
          <div class="account-label">当前账号 <strong>{{ auth.user?.username }}</strong></div>
          <el-alert v-if="failure" :title="failure" type="error" show-icon :closable="false" class="form-error" role="alert" />
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
            <el-form-item label="当前密码" prop="oldPassword" for="old-password"><el-input id="old-password" v-model="form.oldPassword" type="password" show-password autocomplete="current-password" placeholder="请输入当前密码" maxlength="72" :disabled="submitting" /></el-form-item>
            <el-form-item label="新密码" prop="newPassword" for="new-password"><el-input id="new-password" v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="8～64 位，包含字母和数字" maxlength="64" :disabled="submitting" /></el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword" for="confirm-password"><el-input id="confirm-password" v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" placeholder="请再次输入新密码" maxlength="64" :disabled="submitting" /></el-form-item>
            <el-button type="primary" native-type="submit" class="full-width" :loading="submitting">保存新密码并重新登录</el-button>
          </el-form>
          <div class="password-secondary"><el-button v-if="!mandatory" text :icon="ArrowLeft" :disabled="submitting" @click="router.push(safeRedirect(route.query.redirect))">暂不修改，返回工作空间</el-button><el-button v-else text :loading="leaving" :disabled="submitting" @click="leave">退出当前账号</el-button></div>
        </section>
      </div>
      <footer class="auth-footer">CRM 客户关系管理系统 · 账号与访问安全</footer>
    </main>
  </div>
</template>
<style scoped>
.password-header { display: flex; justify-content: space-between; align-items: center; padding: 24px 40px; border-bottom: 1px solid #e3e9f1; background: white; }
.password-header > span { font-size: 12px; }
.password-main { max-width: 920px; margin: 46px auto; padding: 0 24px 24px; }
.password-heading { text-align: center; margin-bottom: 28px; }.password-heading h1 { margin-bottom: 10px; }
.password-card { display: grid; grid-template-columns: 41% 59%; overflow: hidden; }
.password-guide { background: #edf2fc; padding: 42px 30px; }.guide-icon { display: grid; place-items: center; width: 48px; height: 48px; background: white; color: var(--crm-color-brand); font-size: 24px; border-radius: 12px; margin-bottom: 24px; }
.password-guide h2 { font-size: 18px; }.password-guide p { color: var(--crm-text-secondary); line-height: 1.9; margin-bottom: 30px; font-size: 13px; }
.guide-rule { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 16px; font-size: 12px; color: #415c7d; }.guide-rule .el-icon { margin-top: 3px; color: var(--crm-color-brand); flex-shrink: 0; }
.password-form { padding: 32px 40px 20px; }.account-label { display: flex; gap: 12px; color: var(--crm-text-secondary); padding-bottom: 22px; margin-bottom: 22px; border-bottom: 1px solid #e3e9f1; font-size: 12px; overflow-wrap: anywhere; }.account-label strong { color: var(--crm-text-main); }
.password-secondary { text-align: center; padding-top: 12px; }.auth-footer { text-align: center; margin-top: 28px; }
@media (max-width: 700px) { .password-header { padding: 18px 22px; }.password-main { margin-top: 28px; padding: 0 18px 24px; }.password-card { display: block; }.password-guide { padding: 22px; }.guide-icon, .password-guide h2, .password-guide p { display: none; }.guide-rule { margin-bottom: 8px; }.guide-rule:last-child { margin-bottom: 0; }.password-form { padding: 24px; } }
</style>
