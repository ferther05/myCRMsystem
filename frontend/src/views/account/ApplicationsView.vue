<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElDialog, ElMessage, ElRadioGroup, ElRadio } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import { useAuthStore } from '@/store/auth'
import { APPROVAL_STATUS, check, validUsername, validPhone, validInitialPassword } from '@/utils/account-rules'
import * as api from '@/api/users'
const auth = useAuthStore()
const admin = computed(() => auth.user?.roleCode === 'ADMIN')
const rows = ref([]), total = ref(0), page = ref(1), size = ref(20)
const loading = ref(false), failure = ref(''), dialogOpen = ref(false), target = ref(null)
const saving = ref(false), formError = ref(''), formRef = ref()
const form = reactive({ username: '', realName: '', phone: '', email: '', reason: '', approved: true, opinion: '', initialPassword: '' })
const rules = {
  username: [{ validator: check(validUsername, '账号为 3～64 位字母、数字、点、横线或下划线'), trigger: 'blur' }],
  realName: [{ required: true, whitespace: true, max: 80, message: '请输入姓名，最多 80 字', trigger: 'blur' }],
  phone: [{ validator: check(validPhone, '联系电话格式不正确'), trigger: 'blur' }],
  email: [{ type: 'email', max: 160, message: '请输入有效邮箱', trigger: 'blur' }],
  reason: [{ required: true, whitespace: true, max: 1000, message: '请填写申请原因，最多 1000 字', trigger: 'blur' }],
  opinion: [{ required: true, whitespace: true, max: 500, message: '请填写处理意见，最多 500 字', trigger: 'blur' }],
  initialPassword: [{ validator: check(validInitialPassword, '密码需 8～64 位，包含字母和数字，不能有空格或中文'), trigger: 'blur' }],
}
async function load() {
  loading.value = true; failure.value = ''
  try { const data = await api.listApplications({ page: page.value, size: size.value }); rows.value = data.list; total.value = data.total }
  catch (error) { failure.value = error.message }
  finally { loading.value = false }
}
function open(row = null) {
  target.value = row; formError.value = ''
  Object.assign(form, { username: '', realName: '', phone: '', email: '', reason: '', approved: true, opinion: '', initialPassword: '' })
  dialogOpen.value = true
}
async function save() {
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true; formError.value = ''
  try {
    if (admin.value) await api.reviewApplication(target.value.id, { version: target.value.version, approved: form.approved, opinion: form.opinion.trim(), initialPassword: form.approved ? form.initialPassword : null })
    else await api.applyAccount({ username: form.username, realName: form.realName.trim(), phone: form.phone || null, email: form.email || null, reason: form.reason.trim() })
    dialogOpen.value = false; form.initialPassword = ''
    ElMessage.success(admin.value ? '审批已处理' : '申请已提交，审核通过后才能登录')
    await load()
  } catch (error) { formError.value = `${error.message}${error.status === 409 ? '；账号可能已存在或申请已处理，请刷新后核对。' : ''}` }
  finally { saving.value = false }
}
async function resubmit(row) {
  saving.value = true
  try { await api.resubmitApplication(row.id, row.version); ElMessage.success('已重新提交申请'); await load() }
  catch (error) { ElMessage.error(error.message) }
  finally { saving.value = false }
}
watch([page, size], load)
onMounted(load)
</script>
<template>
  <div>
    <PageHeading title="内部账号申请与审核" :description="admin ? '审核团队经理提交的新专员账号申请；通过时设置初始密码。' : `为 ${auth.user?.teamName || '本团队'} 新成员申请专员账号。`"><el-button v-if="!admin" type="primary" @click="open()">提交账号申请</el-button><el-button :loading="loading" @click="load">刷新</el-button></PageHeading>
    <DataTable :rows="rows" :total="total" :loading="loading" :error="failure" v-model:page="page" v-model:size="size" @retry="load">
      <el-table-column prop="username" label="申请账号" min-width="170" /><el-table-column prop="realName" label="成员姓名" min-width="120" /><el-table-column prop="teamName" label="所属团队" min-width="150" /><el-table-column prop="applicantName" label="申请经理" min-width="120" />
      <el-table-column label="审核状态" min-width="105"><template #default="{ row }"><el-tag :type="row.approvalStatus === 'APPROVED' ? 'success' : row.approvalStatus === 'REJECTED' ? 'danger' : 'warning'">{{ APPROVAL_STATUS[row.approvalStatus] }}</el-tag></template></el-table-column>
      <el-table-column prop="reason" label="申请原因" min-width="220" /><el-table-column prop="reviewOpinion" label="审核意见" min-width="220" /><el-table-column prop="submittedAt" label="提交时间" min-width="190" />
      <el-table-column label="操作" min-width="130" fixed="right"><template #default="{ row }"><el-button v-if="admin && row.approvalStatus === 'PENDING'" link type="primary" @click="open(row)">审核申请</el-button><el-button v-else-if="!admin && row.approvalStatus === 'REJECTED'" link type="primary" :disabled="saving" @click="resubmit(row)">重新提交</el-button><span v-else class="muted">{{ row.approvalStatus === 'PENDING' ? '等待管理员' : '已处理' }}</span></template></el-table-column>
    </DataTable>
    <p class="scope-note">申请会保留唯一账号名，审核通过前不能登录。驳回后经理可以重新提交；历史处理动作保存在操作日志中。当前审批页只开放账号申请。</p>
    <el-dialog v-model="dialogOpen" :title="admin ? '审核账号申请' : '申请本团队专员账号'" width="540px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="form.initialPassword = ''; formRef?.resetFields()">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="form-error" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
        <template v-if="!admin"><el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="64" /></el-form-item><el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="80" /></el-form-item><el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" maxlength="32" /></el-form-item><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="160" /></el-form-item><el-form-item label="申请原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><p class="scope-note">角色固定为销售专员，所属团队由你的当前团队自动确定。</p></template>
        <template v-else><p>{{ target?.realName }} · {{ target?.username }} · {{ target?.teamName }}</p><el-form-item label="审核结果"><el-radio-group v-model="form.approved"><el-radio :value="true">通过</el-radio><el-radio :value="false">驳回</el-radio></el-radio-group></el-form-item><el-form-item label="审核意见" prop="opinion"><el-input v-model="form.opinion" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item v-if="form.approved" label="设置初始密码" prop="initialPassword"><el-input v-model="form.initialPassword" type="password" show-password maxlength="64" autocomplete="new-password" /></el-form-item><p v-if="form.approved" class="scope-note">通过后立即启用，首次登录须改密。请妥善告知使用人初始密码。</p></template>
        <el-button type="primary" native-type="submit" :loading="saving">{{ admin ? '确认审核' : '提交申请' }}</el-button><el-button :disabled="saving" @click="dialogOpen = false">取消</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>
<style scoped>
.scope-note { color: var(--crm-text-secondary); font-size: 12px; line-height: 1.8; margin-top: 16px; }:deep(.el-dialog) { max-width: calc(100vw - 24px); }
</style>
