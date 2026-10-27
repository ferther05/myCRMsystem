<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElDialog, ElMessage } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import { http } from '@/api/http'
const route = useRoute()
const sea = computed(() => route.path === '/public-pool' ? 'PUBLIC' : 'PRIVATE')
const rows = ref([]), total = ref(0), page = ref(1), size = ref(10)
const loading = ref(false), failure = ref(''), detail = ref(null), open = ref(false)
let generation = 0
async function load() {
  const current = ++generation
  loading.value = true; failure.value = ''
  try { const data = await http.get('/customers', { params: { sea: sea.value, page: page.value, size: size.value } }); if (generation === current) { rows.value = data.list; total.value = data.total } }
  catch (error) { if (generation === current) failure.value = error.message }
  finally { if (generation === current) loading.value = false }
}
async function show(row) {
  try { detail.value = await http.get(`/customers/${row.id}`); open.value = true }
  catch (error) { ElMessage.error(error.message) }
}
watch(sea, () => { page.value = 1; open.value = false; rows.value = []; void load() })
watch([page, size], load)
void load()
</script>
<template>
  <div>
    <PageHeading :title="sea === 'PUBLIC' ? '客户公海查询' : '客户范围查询'" :description="sea === 'PUBLIC' ? '公海电话由后端脱敏，当前仅开放基础信息查询。' : '专员看本人，经理看本团队，管理员看全量；有效交接期可只读查询。'"><el-button :loading="loading" @click="load">刷新</el-button></PageHeading>
    <el-alert title="当前开放查询与权限隔离。客户新增、编辑、跟进、公海领取等操作将在客户模块继续实现。" type="info" :closable="false" show-icon class="form-error" />
    <DataTable :rows="rows" :total="total" :loading="loading" :error="failure" v-model:page="page" @retry="load"><el-table-column prop="customerName" label="客户名称" min-width="200" /><el-table-column prop="primaryPhone" label="联系电话" min-width="160" /><el-table-column prop="ownerName" label="负责人" min-width="120" /><el-table-column prop="teamName" label="所属团队" min-width="150" /><el-table-column prop="customerStatus" label="客户状态" min-width="120" /><el-table-column label="访问范围" min-width="130"><template #default="{ row }">{{ sea === 'PUBLIC' ? '公海基础信息' : row.readonly ? '交接期只读' : '当前职责范围' }}</template></el-table-column><el-table-column label="操作" min-width="100" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="show(row)">查看详情</el-button></template></el-table-column></DataTable>
    <el-dialog v-model="open" title="客户基础信息" width="520px"><dl v-if="detail"><div v-for="(label,key) in { customerName: '客户名称', primaryPhone: '联系电话', ownerName: '负责人', teamName: '团队', sourceCode: '来源', industryCode: '行业', customerStatus: '状态' }" :key="key"><dt>{{ label }}</dt><dd>{{ detail[key] || '未填写' }}</dd></div></dl></el-dialog>
  </div>
</template>
<style scoped>
dl > div { display: grid; grid-template-columns: 100px 1fr; padding: 10px 0; }dt { color: var(--crm-text-secondary); }dd { margin: 0; overflow-wrap: anywhere; }:deep(.el-dialog) { max-width: calc(100vw - 24px); }
</style>
