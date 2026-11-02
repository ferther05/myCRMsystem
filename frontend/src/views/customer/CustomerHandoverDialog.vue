<!-- 文件作用：管理员同团队批量交接，先预览数量再确认，业务变更时重新预览。 -->
<script setup>
import { computed, ref, watch } from 'vue';
import { ElDialog, ElSelect, ElOption, ElCheckbox, ElMessage, ElMessageBox } from 'element-plus';
import PagedItems from '@/components/PagedItems.vue';
import * as api from '@/api/customers';
const open=defineModel({type:Boolean,default:false});
const emit=defineEmits(['done']);
const people=ref([]), rows=ref([]), selected=ref([]), source=ref(null), target=ref(null), reason=ref('');
const preview=ref(null), busy=ref(false), failure=ref('');
const sourcePerson=computed(()=>people.value.find(x=>x.id===source.value));
const targets=computed(()=>people.value.filter(x=>x.team_id===sourcePerson.value?.team_id&&x.id!==source.value&&x.role_code==='SPECIALIST'&&x.account_status==='ACTIVE'));
let generation=0;
/** 每次打开重新读取账号，避免停用或调岗后仍用旧选项。 */
watch(open,async value=>{
  if(!value)return;
  source.value=null;target.value=null;selected.value=[];rows.value=[];preview.value=null;reason.value='';failure.value='';
  try {people.value=await api.handoverOwners();}catch(e){failure.value=e.message;}
});
/** 换移出人时清除选择，防止旧列表响应覆盖新列表。 */
watch(source,async value=>{
  const current=++generation;target.value=null;rows.value=[];selected.value=[];preview.value=null;
  if(!value)return;
  try {const data=await api.handoverCandidates(value);if(current===generation)rows.value=data;}catch(e){if(current===generation)failure.value=e.message;}
});
watch([target,selected],()=>{preview.value=null;},{deep:true});
/** 客户勾选跨分页保留；最多50个，服务端也会校验。 */
function toggle(id,value){selected.value=value?[...selected.value,id]:selected.value.filter(x=>x!==id);}
const selection=()=>({fromOwnerId:source.value,toOwnerId:target.value,customerIds:selected.value});
/** 获取关联业务数量与版本指纹，尚不修改归属。 */
async function inspect(){
  if(!source.value||!target.value||!selected.value.length||selected.value.length>50){ElMessage.warning('请选择原负责人、同团队接收专员及1～50个客户');return;}
  busy.value=true;failure.value='';
  try {preview.value=await api.previewHandover(selection());}catch(e){failure.value=e.message;}finally{busy.value=false;}
}
/** 二次确认后整批执行；版本冲突时清空旧预览。 */
async function execute(){
  if(!preview.value||!reason.value.trim()){ElMessage.warning('请先预览并填写交接原因');return;}
  try {
    await ElMessageBox.confirm('所选客户的全部商机、合同和原负责人的未处理待办将一起交接。历史作者及历史业绩不变；原专员保留7天客户只读，原经理仍按团队权限查看。','确认批量交接',{type:'warning'});
    busy.value=true;await api.executeHandover({selection:selection(),previewToken:preview.value.previewToken,reason:reason.value.trim()});
    ElMessage.success('所选客户及关联业务已完整交接');open.value=false;emit('done');
  }catch(e){if(e instanceof Error){failure.value=e.message;preview.value=null;}}finally{busy.value=false;}
}
</script>
<template>
  <el-dialog v-model="open" title="同团队批量交接" width="820px" style="max-width:calc(100vw - 24px)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <el-alert title="管理员专用：每批最多50个客户。原负责人可为停用账号；接收人必须是同团队启用专员。" type="info" :closable="false" />
    <el-form label-position="top" :disabled="busy" class="handover-form">
      <el-form-item label="原负责人"><el-select v-model="source" filterable placeholder="选择移出账号"><el-option v-for="p in people" :key="p.id" :value="p.id" :label="`${p.real_name || p.username} · ${p.team_name}${p.account_status==='ACTIVE'?'':'（未启用）'}`" /></el-select></el-form-item>
      <el-form-item label="接收专员"><el-select v-model="target" filterable placeholder="选择同团队启用专员"><el-option v-for="p in targets" :key="p.id" :value="p.id" :label="p.real_name || p.username" /></el-select></el-form-item>
      <el-form-item label="交接原因"><el-input v-model="reason" type="textarea" maxlength="500" :rows="2" show-word-limit placeholder="例如离职交接、工作分配调整" /></el-form-item>
    </el-form>
    <p>已选择 {{ selected.length }} 个客户 · 共 {{ rows.length }} 个可选客户</p>
    <PagedItems :items="rows"><template #default="{rows:items}"><el-table :data="items" max-height="330" empty-text="请选择原负责人"><el-table-column label="选择" width="65"><template #default="{row}"><el-checkbox :model-value="selected.includes(row.id)" :disabled="busy || (!selected.includes(row.id) && selected.length>=50)" :aria-label="`选择${row.customer_name}`" @change="v=>toggle(row.id,v)" /></template></el-table-column><el-table-column prop="customer_name" label="客户名称" min-width="240" /></el-table></template></PagedItems>
    <el-alert v-if="preview" class="preview" type="success" :closable="false" :title="`将交接 ${preview.customers} 个客户、${preview.opportunities} 条商机、${preview.contracts} 份合同、${preview.pendingTodos} 条原负责人未处理待办。`" />
    <el-alert v-if="failure" type="error" :title="failure" :closable="false" />
    <template #footer><el-button :disabled="busy" @click="open=false">取消</el-button><el-button :loading="busy" @click="inspect">预览交接</el-button><el-button type="primary" :disabled="!preview" :loading="busy" @click="execute">确认交接</el-button></template>
  </el-dialog>
</template>
<style scoped>.handover-form{margin-top:16px}.handover-form :deep(.el-select){width:100%}.preview{margin:14px 0}</style>
