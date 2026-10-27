<script setup>
import { ref } from 'vue'
import { ElSelect, ElOption, ElDatePicker, ElCheckbox } from 'element-plus'
import { contactRoles } from '@/utils/customer-rules'
defineProps({ model: { type: Object, required: true }, primaryLocked: Boolean })
const form = ref()
const rules = {
  contactName: [{ required: true, whitespace: true, message: '请输入联系人姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }, { pattern: /^[+0-9() -]{5,32}$/, message: '请输入5～32位电话，可含区号、空格和短横线', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}
defineExpose({ validate: () => form.value.validate() })
</script>
<template>
  <el-form ref="form" :model="model" :rules="rules" label-position="top" class="contact-grid">
    <el-form-item label="联系人姓名" prop="contactName"><el-input v-model="model.contactName" maxlength="80" /></el-form-item>
    <el-form-item label="联系电话" prop="phone"><el-input v-model="model.phone" maxlength="32" /></el-form-item>
    <el-form-item label="职务" prop="jobTitle"><el-input v-model="model.jobTitle" maxlength="80" /></el-form-item>
    <el-form-item label="联系角色" prop="decisionRole"><el-select v-model="model.decisionRole"><el-option v-for="(label, code) in contactRoles" :key="code" :value="code" :label="label" /></el-select></el-form-item>
    <el-form-item label="邮箱" prop="email"><el-input v-model="model.email" maxlength="160" /></el-form-item>
    <el-form-item label="生日" prop="birthday"><el-date-picker v-model="model.birthday" type="date" value-format="YYYY-MM-DD" :disabled-date="(date) => date > new Date()" /></el-form-item>
    <el-form-item label="主要联系人" class="span-2"><el-checkbox v-model="model.primary" :disabled="primaryLocked">设为主要联系人（同步客户主电话）</el-checkbox></el-form-item>
  </el-form>
</template>
<style scoped>
.contact-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px; }.span-2 { grid-column:1 / -1; }.el-select,:deep(.el-date-editor.el-input) { width:100%; }@media(max-width:600px) { .contact-grid { grid-template-columns:1fr; } }
</style>
