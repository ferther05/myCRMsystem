<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Refresh, User } from '@element-plus/icons-vue'
import PageHeading from '@/components/PageHeading.vue'
import { useAuthStore } from '@/store/auth'
import { ROLE_NAMES } from '@/utils/auth-rules'
import { check, validPhone } from '@/utils/account-rules'
const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const failure = ref('')
const formRef = ref()
const form = reactive({ realName: '', phone: '', email: '' })
const name = computed(() => ROLE_NAMES[auth.user?.roleCode])
const rules = {
  realName: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }, { max: 80, message: '姓名最多 80 字' }],
  phone: [{ validator: check(validPhone, '请输入 6～32 位联系电话，可包含数字、空格、加号和括号'), trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }, { max: 160, message: '邮箱最多 160 字' }],
}
function edit() {
  Object.assign(form, { realName: auth.user.realName, phone: auth.user.phone || '', email: auth.user.email || '' })
  failure.value = ''
  editing.value = true
}
async function save() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  failure.value = ''
  try {
    await auth.updateProfile({ realName: form.realName.trim(), phone: form.phone.trim() || null, email: form.email.trim() || null })
    editing.value = false
    ElMessage.success('个人资料已保存')
  } catch (error) { failure.value = error.message }
  finally { saving.value = false }
}
async function refresh() {
  loading.value = true
  failure.value = ''
  try { await auth.ensureUser(true) }
  catch (error) { failure.value = error.message }
  finally { loading.value = false }
}
</script>
<template>
  <div>
    <PageHeading title="个人中心" description="维护本人姓名和联系方式，查看角色与所属团队。"><el-button :icon="Refresh" :loading="loading" :disabled="saving || editing" @click="refresh">刷新信息</el-button></PageHeading>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon class="form-error" role="alert" />
    <section v-loading="loading" class="panel profile-panel">
      <div class="profile-header"><div class="profile-avatar"><el-icon><User /></el-icon></div><div><h2>{{ auth.user?.realName }}</h2><p>{{ name }} · {{ auth.user?.teamName || '系统管理工作空间' }}</p></div></div>
      <dl><div><dt>登录账号</dt><dd>{{ auth.user?.username }}</dd></div><div><dt>当前角色</dt><dd>{{ name }}</dd></div><div><dt>所属团队</dt><dd>{{ auth.user?.teamName || '未分配团队' }}</dd></div></dl>
      <el-form v-if="editing" ref="formRef" :model="form" :rules="rules" label-position="top" class="profile-form" @submit.prevent="save">
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="80" :disabled="saving" /></el-form-item>
        <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" maxlength="32" autocomplete="tel" :disabled="saving" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="160" autocomplete="email" :disabled="saving" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="saving">保存资料</el-button><el-button :disabled="saving" @click="editing = false">取消</el-button>
      </el-form>
      <template v-else><dl><div><dt>姓名</dt><dd>{{ auth.user?.realName }}</dd></div><div><dt>联系电话</dt><dd>{{ auth.user?.phone || '未填写' }}</dd></div><div><dt>邮箱</dt><dd>{{ auth.user?.email || '未填写' }}</dd></div></dl><el-button type="primary" @click="edit">编辑资料</el-button></template>
      <p class="profile-note">登录账号不可修改；角色和团队由管理员维护。保存资料后当前页面会自动更新身份。</p>
    </section>
    <section class="panel account-security"><div><h2><el-icon><Lock /></el-icon> 登录密码</h2><p>修改密码后需要重新登录。</p></div><el-button type="primary" plain :icon="Lock" @click="router.push({ path: '/change-password', query: { redirect: '/account' } })">修改密码</el-button></section>
  </div>
</template>
<style scoped>
.profile-panel { padding: 28px; max-width: 1000px; }.profile-header { display: flex; align-items: center; gap: 18px; padding-bottom: 20px; border-bottom: 1px solid #e3e9f1; }.profile-avatar { width: 56px; height: 56px; border-radius: 12px; background: #eaf0ff; color: #245ee5; display: grid; place-items: center; font-size: 26px; }.profile-header h2 { margin-bottom: 5px; font-size: 20px; }.profile-header p,.profile-note { color: var(--crm-text-secondary); font-size: 12px; }dl { margin: 12px 0; }dl > div { display: grid; grid-template-columns: 110px 1fr; padding: 10px 0; font-size: 13px; }dt { color: var(--crm-text-secondary); }dd { margin: 0; overflow-wrap: anywhere; }.profile-form { max-width: 520px; margin-top: 20px; }.profile-note { margin: 18px 0 0; line-height: 1.8; }.account-security { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 25px 28px; margin-top: 20px; max-width: 1000px; }.account-security h2 { font-size: 15px; display: flex; align-items: center; gap: 8px; }.account-security p { margin: 6px 0 0; color: var(--crm-text-secondary); font-size: 12px; }
@media (max-width: 600px) { .profile-panel { padding: 20px; }dl > div { grid-template-columns: 86px 1fr; }.account-security { padding: 20px; flex-direction: column; align-items: flex-start; } }
</style>
