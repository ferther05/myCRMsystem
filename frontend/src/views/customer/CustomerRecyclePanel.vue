<!-- 文件作用：客户详情内展示回收条件、延期申请与经理/管理员审批历史。 -->
<script setup>
import { ref, reactive, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PagedItems from '@/components/PagedItems.vue';
import * as api from '@/api/recycle';
const props = defineProps({ customer: { type: Object, required: true } });
const emit = defineEmits(['changed']);
const data = ref(null), loading = ref(false), error = ref(''), busy = ref(false), mode = ref('');
const form = reactive({ reason: '', nextPlan: '' });
const statuses = { PENDING: '待审核', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '周期已变更，已取消' };
const actions = { CUSTOMER_RECYCLE_WARNING: '发送预警', RECYCLE_APPEAL_SUBMITTED: '申请延期', RECYCLE_APPEAL_REVIEWED: '延期审核', CUSTOMER_RECYCLED: '回收公海', PUBLIC_CLAIM: '普通领取', PRIORITY_CLAIM: '优先重新领取' };
let generation = 0;
/** 所有业务时间展示到秒。 */
const time = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '—';
/** 切换客户后加载本客户授权信息。 */
async function load() {
  const current = ++generation;
  loading.value = true; error.value = ''; data.value = null;
  try { const result = await api.detail(props.customer.id); if (current === generation) data.value = result; }
  catch (e) { if (current === generation) error.value = e.message; }
  finally { if (current === generation) loading.value = false; }
}
/** 负责人提交原因与明确的后续计划，成功后重读审批事实。 */
async function submit() {
  if (!form.reason.trim() || !form.nextPlan.trim()) return ElMessage.warning('请填写延期原因和后续跟进计划');
  busy.value = true;
  try { await api.appeal(props.customer.id, { version: props.customer.version, reason: form.reason.trim(), nextPlan: form.nextPlan.trim() }); mode.value = ''; form.reason = ''; form.nextPlan = ''; ElMessage.success('延期申请已提交，等待审核'); await load(); emit('changed'); }
  catch (e) { ElMessage.error(e.message); }
  finally { busy.value = false; }
}
/** 核验必须填写意见，禁止自己审核自己的申请。 */
async function review(row, pass) {
  try {
    const result = await ElMessageBox.prompt('请填写审核意见；如跟进或归属周期已变更，系统将取消旧申请。', pass ? '批准延期7天' : '驳回延期', { inputValidator: (v) => !!v?.trim() && v.trim().length <= 500 || '请填写1～500字意见' });
    busy.value = true; await api.review(row.id, { version: row.version, pass, opinion: result.value.trim() }); ElMessage.success('已完成核验，请查看最新状态'); await load(); emit('changed');
  } catch (e) { if (!['cancel', 'close'].includes(e)) ElMessage.error(e.message); }
  finally { busy.value = false; }
}
watch(() => props.customer.id, load, { immediate: true });
</script>
<template>
  <section v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error" @click="load">重试</el-button>
    <template v-if="data">
      <el-alert :title="data.rule.reason" :type="data.rule.decision === 'ELIGIBLE' ? 'warning' : 'info'" :closable="false" />
      <div class="recycle-info"><p>预计预警：{{ time(data.rule.warningAt) }} · {{ data.rule.warned ? '已提醒' : '系统到时自动提醒' }}</p><p>{{ data.rule.decision === 'PROTECTED' ? '参考期限（当前业务受保护）' : '最早回收期限' }}：{{ time(data.rule.deadlineAt) }}</p><p>负责人：及时登记真实有效跟进，或申请延期7天。经理：处理延期申请。系统自动检查，到期且无业务保护才会回收，无需人工逐条回收。</p></div>
      <el-button v-if="data.canAppeal" :disabled="busy" @click="mode = mode ? '' : 'appeal'">申请延期</el-button>
      <el-form v-if="mode === 'appeal'" label-position="top" class="appeal-form" @submit.prevent="submit">
        <el-form-item label="延期原因"><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="后续跟进计划"><el-input v-model="form.nextPlan" type="textarea" maxlength="500" show-word-limit /></el-form-item>
        <p>每客户每季度仅可申请一次；批准后从原期限与批准时间中较晚者加7天。</p>
        <el-button type="primary" native-type="submit" :loading="busy">提交申请</el-button>
      </el-form>
      <h4>延期审批历史</h4>
      <PagedItems :items="data.appeals" empty-text="暂无延期申请"><template #default="{ rows }">
        <el-table :data="rows"><el-table-column label="状态" min-width="155"><template #default="{ row }">{{ statuses[row.status] || row.status }}</template></el-table-column>
          <el-table-column label="原因 / 计划 / 意见" min-width="270"><template #default="{ row }"><p>{{ row.reason }}</p><p>计划：{{ row.nextPlan }}</p><p v-if="row.opinion">审核：{{ row.opinion }}</p></template></el-table-column>
          <el-table-column label="人员 / 时间" min-width="190"><template #default="{ row }"><p>{{ row.applicantName }} · {{ time(row.submittedAt) }}</p><p v-if="row.processedAt">{{ row.approverName || '系统自动处理' }} · {{ time(row.processedAt) }}</p></template></el-table-column>
          <el-table-column label="操作" width="145"><template #default="{ row }"><template v-if="row.canReview"><el-button link type="primary" :disabled="busy" @click="review(row, true)">批准</el-button><el-button link type="danger" :disabled="busy" @click="review(row, false)">驳回</el-button></template></template></el-table-column>
        </el-table>
      </template></PagedItems>
      <h4>预警与归属历史</h4>
      <PagedItems :items="data.events || []"><template #default="{ rows }">
        <el-table :data="rows" empty-text="暂无相关记录"><el-table-column label="操作" min-width="130"><template #default="{ row }">{{ actions[row.action] || row.action }}</template></el-table-column><el-table-column prop="operatorName" label="操作人" min-width="120" /><el-table-column label="时间" min-width="180"><template #default="{ row }">{{ time(row.occurredAt) }}</template></el-table-column><el-table-column prop="reason" label="说明" min-width="260" show-overflow-tooltip /></el-table>
      </template></PagedItems>
    </template>
  </section>
</template>
<style scoped>
.recycle-info { color: var(--crm-text-secondary); line-height: 1.7; margin: 14px 0; }
.appeal-form { max-width: 680px; padding: 16px 0; }
.appeal-form p { color: var(--crm-text-secondary); font-size: 13px; }
</style>
