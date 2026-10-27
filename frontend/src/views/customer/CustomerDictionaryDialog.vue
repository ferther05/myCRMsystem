<script setup>
import PagedItems from '@/components/PagedItems.vue'
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElDialog, ElRadioGroup, ElRadioButton, ElInputNumber, ElSwitch } from 'element-plus'
import { useCustomerDictionaries } from '@/store/customer-dictionaries'
import * as api from '@/api/customers'
const open = defineModel({ type: Boolean })
const dictionaries = useCustomerDictionaries(), group = ref('tag'), editing = ref(false), saving = ref(false), failure = ref(''), form = ref()
const groups = { tag: '客户标签', source: '客户来源', industry: '所属行业' }
const model = reactive({ id: null, version: 0, group: 'tag', code: '', label: '', sortNo: 10, enabled: true })
const rows = computed(() => dictionaries.rows.filter((row) => row.group === group.value))
const rules = { code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,31}$/, message: '以大写字母开头，最多32位大写字母、数字或下划线', trigger: 'blur' }], label: [{ required: true, whitespace: true, message: '请输入名称', trigger: 'blur' }] }
function edit(row) {
  failure.value = ''; Object.assign(model, { id: null, version: 0, group: group.value, code: '', label: '', sortNo: 10, enabled: true }, row || {}); editing.value = true
}
async function save() {
  if (!await form.value.validate().catch(() => false)) return
  saving.value = true; failure.value = ''
  try {
    if (model.id) await api.editCustomerDictionary(model.id, { version: model.version, label: model.label, sortNo: model.sortNo, enabled: model.enabled })
    else await api.createCustomerDictionary({ group: model.group, code: model.code, label: model.label, sortNo: model.sortNo })
    await dictionaries.load(); editing.value = false; ElMessage.success('分类选项已保存')
  } catch (error) { failure.value = error.message; if (error.code === 40900) await dictionaries.load().catch(() => {}) }
  finally { saving.value = false }
}
watch(open, () => { failure.value = ''; editing.value = false })
</script>
<template>
  <el-dialog v-model="open" class="customer-dialog" title="分类选项管理" width="760px" :close-on-click-modal="false">
    <p class="hint">管理员可维护标签、来源和行业。编码固定；停用后历史记录保留，新录入时不可选。客户等级保持重点、常规、观望三档。</p>
    <div class="toolbar"><el-radio-group v-model="group"><el-radio-button v-for="(label, code) in groups" :key="code" :value="code">{{ label }}</el-radio-button></el-radio-group><el-button type="primary" @click="edit()">新增选项</el-button></div>
    <PagedItems :items="rows" v-slot="{ rows: pageRows }"><el-table :data="pageRows" max-height="440" empty-text="暂无分类选项"><el-table-column prop="code" label="编码" min-width="150" /><el-table-column prop="label" label="名称" min-width="130" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column prop="sortNo" label="顺序" width="75" /><el-table-column label="操作" width="85"><template #default="{ row }"><el-button type="primary" link @click="edit(row)">编辑</el-button></template></el-table-column></el-table></PagedItems>
    <el-dialog v-model="editing" class="customer-dialog" :title="model.id ? '编辑分类选项' : '新增分类选项'" width="480px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-alert v-if="failure" :title="failure" type="error" show-icon :closable="false" class="form-error" />
      <el-form ref="form" :model="model" :rules="rules" label-position="top"><el-form-item label="编码" prop="code"><el-input v-model="model.code" maxlength="32" :disabled="Boolean(model.id)" /></el-form-item><el-form-item label="显示名称" prop="label"><el-input v-model="model.label" maxlength="40" /></el-form-item><el-form-item label="显示顺序"><el-input-number v-model="model.sortNo" :min="0" :max="9999" /></el-form-item><el-form-item v-if="model.id" label="状态"><el-switch v-model="model.enabled" active-text="启用" inactive-text="停用" /></el-form-item></el-form>
      <template #footer><el-button :disabled="saving" @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存选项</el-button></template>
    </el-dialog>
  </el-dialog>
</template>
<style scoped>.hint { color:var(--crm-text-secondary);line-height:1.7; }.toolbar { display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;margin:16px 0; }</style>
