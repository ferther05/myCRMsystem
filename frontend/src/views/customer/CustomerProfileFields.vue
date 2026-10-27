<script setup>
import { ref } from 'vue'
import { ElSelect, ElOption } from 'element-plus'
import { useCustomerDictionaries } from '@/store/customer-dictionaries'
const props = defineProps({ model: { type: Object, required: true }, publicProfile: Boolean })
const dictionaries = useCustomerDictionaries(), form = ref()
const rules = {
  customerName: [{ required: true, whitespace: true, message: '请输入客户名称', trigger: 'blur' }, { max: 160, message: '最多160个字符', trigger: 'blur' }],
  customerLevel: [{ required: true, message: '请选择客户等级', trigger: 'change' }],
  unifiedSocialCreditCode: [{ pattern: /^(?:[0-9A-Za-z]{18})?$/, message: '请输入18位信用代码，或留空', trigger: 'blur' }],
}
defineExpose({ validate: () => form.value.validate() })
</script>
<template>
  <el-form ref="form" :model="props.model" :rules="rules" label-position="top" class="profile-grid">
    <el-form-item label="客户名称" prop="customerName" class="span-2"><el-input v-model="model.customerName" maxlength="160" show-word-limit /></el-form-item>
    <el-form-item label="客户来源" prop="sourceCode"><el-select v-model="model.sourceCode" clearable placeholder="请选择来源"><el-option v-for="item in dictionaries.options('source', model.sourceCode)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
    <el-form-item label="所属行业" prop="industryCode"><el-select v-model="model.industryCode" clearable placeholder="请选择行业"><el-option v-for="item in dictionaries.options('industry', model.industryCode)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
    <el-form-item label="客户等级" prop="customerLevel"><el-select v-model="model.customerLevel"><el-option v-for="item in dictionaries.options('level', model.customerLevel)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
    <el-form-item label="所在地区" prop="region"><el-input v-model="model.region" maxlength="100" placeholder="例如：上海市浦东新区" /></el-form-item>
    <el-form-item label="客户标签（最多10个）" prop="tags" class="span-2"><el-select v-model="model.tags" multiple filterable :multiple-limit="10" placeholder="可选择多个标签"><el-option v-for="item in dictionaries.options('tag', model.tags)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
    <template v-if="!publicProfile">
      <el-form-item label="统一社会信用代码" prop="unifiedSocialCreditCode"><el-input v-model="model.unifiedSocialCreditCode" maxlength="18" /></el-form-item>
      <el-form-item label="公司规模" prop="companySize"><el-input v-model="model.companySize" maxlength="32" placeholder="例如：20～50人" /></el-form-item>
      <el-form-item label="客户备注" prop="remark" class="span-2"><el-input v-model="model.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
    </template>
  </el-form>
</template>
<style scoped>
.profile-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 20px; }.span-2 { grid-column: 1 / -1; }.el-select { width: 100%; }@media(max-width:600px) { .profile-grid { grid-template-columns:1fr; } }
</style>
