<!-- 文件作用：集中查询本人待办、授权操作摘要及管理员执行记录。 -->
<script setup>
import { useAutoQuery } from '@/composables/useAutoQuery';
import DetailSection from '@/components/DetailSection.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElDescriptions, ElDescriptionsItem, ElMessage, ElMessageBox, ElTabs, ElTabPane, ElSelect, ElOption, ElDatePicker, ElCheckbox, ElDialog } from 'element-plus';
import PagedItems from '@/components/PagedItems.vue';
import PageHeading from '@/components/PageHeading.vue';
import DataTable from '@/components/DataTable.vue';
import { useAuthStore } from '@/store/auth';
import * as api from '@/api/tasks';

const router = useRouter();
const todoDetail = ref(null);
const operationSummary = ref(null);
const moreFilters = ref(false);
// 收起低频条件时保留已选提示，查询条件仍按原契约发送。
const extraFiltersActive = computed(() => Boolean(filters.from || filters.to || filters.bizId || filters.action));
const auth = useAuthStore();
const admin = computed(() => auth.user?.roleCode === 'ADMIN');
const tab = ref('todos'), page = ref(1), rows = ref([]), total = ref(0);
const loading = ref(false), failure = ref(''), acting = ref(false);
const filters = reactive({ status: 'PENDING', type: '', unreadOnly: false, from: '', to: '', bizType: '', bizId: '', action: '', jobName: '', runStatus: '' });
const operationOpen=ref(false), operation=ref(null), operationLoading=ref(false), operationError=ref('');
const jobName = ref('payment-overdue-scan');
const types = { CONTRACT_REVIEW:'合同登记或文件核验', CARE_BIRTHDAY:'联系人生日关怀', FOLLOW_DUE:'下次跟进提醒', CONTRACT_STAGNANT:'合同办理停滞', CUSTOMER_WARNING: '客户回收预警', PAYMENT_OVERDUE: '回款逾期', OPP_STAGNANT: '商机停滞', OPP_CLOSE_PENDING: '商机待裁决', OPP_ESCALATED: '商机裁决超时', APPROVAL: '业务审批' };
// 从看板进入时只接受已知待办类型，其他筛选仍使用页面默认值。
const route = useRoute();
if (Object.hasOwn(types, route.query.type)) filters.type = route.query.type;
/** 工作台链接仅接受已开放页签；任务执行页仍限管理员。 */
function entryTab(value) {
  if (value === 'operations') return 'operations';
  if (value === 'jobs' && admin.value) return 'jobs';
  return 'todos';
}
tab.value = entryTab(route.query.tab);
const todoStatuses = { PENDING: '待处理', DONE: '已办理', RESOLVED: '业务已解除', CANCELLED: '已取消' };
const bizTypes = { SYSTEM_CONFIG: '系统参数', CUSTOMER: '客户', OPPORTUNITY: '商机', CONTRACT: '合同', USER: '账号', TODO: '待办', JOB: '扫描任务' };
const jobs = { 'business-reminder-scan':'跟进与合同提醒扫描', 'customer-recycle-scan': '客户公海回收扫描', 'opportunity-activity-scan': '商机活跃度扫描', 'payment-overdue-scan': '回款逾期扫描' };
const runStatuses = { RUNNING: '执行中', SUCCESS: '成功', PARTIAL_FAILURE: '部分失败', FAILED: '失败' };
let generation = 0;

/** 所有展示时间统一到秒，空时间使用缺省文案。 */
const time = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
/** 只发送当前页签的筛选参数，避免其他页签残留条件影响查询。 */
function params() {
  const result = { page: page.value, size: 10, from: filters.from || undefined, to: filters.to || undefined };
  if (tab.value === 'todos') Object.assign(result, { status: filters.status || undefined, type: filters.type || undefined, unreadOnly: filters.unreadOnly || undefined });
  if (tab.value === 'operations') Object.assign(result, { bizType: filters.bizType || undefined, bizId: filters.bizId || undefined, action: filters.action.trim() || undefined });
  if (tab.value === 'jobs') Object.assign(result, { jobName: filters.jobName || undefined, runStatus: filters.runStatus || undefined });
  return result;
}
/** 请求标记防止慢响应覆盖已切换页签的数据。 */
async function load() {
  const current = ++generation;
  loading.value = true; failure.value = '';
  if (filters.from && filters.to && filters.from > filters.to) {
    failure.value = '开始日期不能晚于结束日期'; loading.value = false; return;
  }
  try {
    const request = { todos: api.listTodos, operations: api.listOperations, jobs: api.listJobs }[tab.value];
    const result = await request(params());
    if (current === generation) { rows.value = result.list; total.value = result.total; }
  } catch (error) { if (current === generation) { failure.value = error.message; rows.value = []; total.value = 0; } }
  finally { if (current === generation) loading.value = false; }
}
/** 筛选后回到第一页。 */
const { search } = useAutoQuery({ filters: () => ({ ...params(), page: undefined, tab: tab.value }), page, load,
  invalidate: () => { ++generation; rows.value = []; loading.value = true; },
});
/** 已读不隐藏欠款或代替审批，仍保留待处理状态。 */
async function markRead(row) {
  if (acting.value) return;
  acting.value = true;
  try { await api.readTodo(row.id, row.version); ElMessage.success('已标记阅读，业务状态未改变'); await load(); }
  catch (error) { ElMessage.error(error.message); }
  finally { acting.value = false; }
}
/** 跳转前从后端再次取得当前有权访问的位置。 */
async function openBusiness(row) {
  try { const target = await api.todoTarget(row.id); await router.push(target.path); }
  catch (error) { ElMessage.error(error.message); await load(); }
}
/** 失败清单仅作管理员定位，无法由前端编辑后传回执行。 */
function failedIds(row) {
  try { return JSON.parse(row.failedBizIdsJson || '[]'); } catch { return []; }
}
/** 执行扫描或补跑前明确提示真实业务影响。 */
async function run(row) {
  if (acting.value) return;
  try {
    await ElMessageBox.confirm(row ? '仅重试本条最新执行记录中的失败对象，按当前业务事实重新检查；客户回收仍受回收开关与保护条件约束。' : '将扫描当天实际业务，可能更新商机、回款并生成提醒；选择客户回收扫描且回收开关开启时，会实际回收符合条件的客户。', row ? '补跑失败项' : '执行当天扫描', { type: 'warning' });
    acting.value = true;
    const result = row ? await api.retryJob(row.id) : await api.scanJob(jobName.value);
    ElMessage[result.status === 'SUCCESS' ? 'success' : 'warning'](`执行结束：成功处理 ${result.processed} 项，失败 ${result.failedIds.length} 项`);
    await load();
  } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message); }
  finally { acting.value = false; }
}

/** 按对象编号回溯同一业务的操作，并查看白名单字段变化。 */
async function showOperation(row) {
  operationSummary.value = row;
  operationOpen.value=true;operation.value=null;operationError.value='';operationLoading.value=true;
  try {operation.value=await api.operationDetail(row.id);}catch(e){operationError.value=e.message;}finally{operationLoading.value=false;}
}
function trace(row){filters.bizType=row.bizType;filters.bizId=row.bizId;filters.action='';filters.from='';filters.to='';}

// 同页面切换工作台捷径时同步入口条件，避免沿用上一次待办类型。
watch(() => route.query, (query) => {
  filters.type = Object.hasOwn(types, query.type) ? query.type : '';
  const next = entryTab(query.tab);
  tab.value = next;
});
onMounted(load);
</script>

<template>
  <div class="tasks-page">
    <PageHeading title="任务与日志" description="查看本人提醒、追溯业务操作；管理员可检查扫描结果与补跑失败项。" />
    <div class="panel task-panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="我的待办" name="todos" />
        <el-tab-pane label="操作记录" name="operations" />
        <el-tab-pane v-if="admin" label="任务执行" name="jobs" />
      </el-tabs>
      <el-form inline class="filters" @submit.prevent="search">
        <template v-if="tab === 'todos'">
          <el-form-item label="业务状态"><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="(label, value) in todoStatuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="提醒类型"><el-select v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item><el-checkbox v-model="filters.unreadOnly">仅未读待处理</el-checkbox></el-form-item>
        </template>
        <template v-if="tab === 'operations'">
          <el-form-item v-show="moreFilters" label="对象编号"><el-input v-model="filters.bizId" clearable placeholder="例如客户或合同编号" /></el-form-item>
          <el-form-item label="业务类型"><el-select v-model="filters.bizType" clearable placeholder="全部类型"><el-option v-for="(label, value) in bizTypes" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item v-show="moreFilters" label="操作编码"><el-input v-model="filters.action" maxlength="64" clearable placeholder="例如 PAYMENT_RECEIPT_CREATED" /></el-form-item>
        </template>
        <template v-if="tab === 'jobs'">
          <el-form-item label="扫描任务"><el-select v-model="filters.jobName" clearable placeholder="全部任务"><el-option v-for="(label, value) in jobs" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="执行状态"><el-select v-model="filters.runStatus" clearable placeholder="全部状态"><el-option v-for="(label, value) in runStatuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        </template>
        <el-form-item v-show="moreFilters" :label="tab === 'operations' ? '操作日期' : '业务日期'"><el-date-picker v-model="filters.from" value-format="YYYY-MM-DD" type="date" placeholder="开始日期" /></el-form-item>
        <el-form-item v-show="moreFilters"><el-date-picker v-model="filters.to" value-format="YYYY-MM-DD" type="date" placeholder="结束日期" /></el-form-item>
        <el-form-item><span class="auto-query-note">自动筛选</span><el-button @click="load">刷新</el-button><el-button text @click="moreFilters = !moreFilters">{{ moreFilters ? '收起筛选' : '更多筛选' }}{{ extraFiltersActive ? '（已选）' : '' }}</el-button></el-form-item>
      </el-form>
      <el-alert v-if="tab === 'todos'" title="标记已读只表示看过提醒；欠款、审批等业务须进入详情实际处理，解决后由系统解除。每个账号只办理自己的待办。" type="info" :closable="false" />
      <p v-if="tab === 'operations'" class="scope-note">专员查看本人业务，经理查看当前本团队业务，管理员查看全部。点击“变更对比”查看脱敏字段变化，“追溯此对象”汇总其记录；旧记录缺失快照不补造。</p>
      <div v-if="tab === 'jobs'" class="run-toolbar">
        <el-select v-model="jobName" aria-label="选择手动扫描任务"><el-option v-for="(label, value) in jobs" :key="value" :label="label" :value="value" /></el-select>
        <el-button type="primary" :loading="acting" @click="run(null)">执行当天扫描</el-button>
        <span class="scope-note">补跑只支持最新失败记录；已成功对象不重跑。客户自动回收是否执行取决于管理员回收开关。</span>
      </div>
    </div>
    <DataTable :rows="rows" :total="total" v-model:page="page" :loading="loading" :error="failure" @retry="load">
      <template v-if="tab === 'todos'">
        <el-table-column label="提醒事项" min-width="230"><template #default="{ row }"><strong>{{ row.title }}</strong><el-tag v-if="!row.readAt && row.status === 'PENDING'" size="small" type="warning" style="margin-left:8px">未读</el-tag><p class="scope-note">{{ row.target?.label || '关联对象不可访问' }}</p></template></el-table-column>
        <el-table-column label="类型" width="140"><template #default="{ row }">{{ types[row.type] || row.type }}</template></el-table-column>
        <el-table-column label="状态" width="125"><template #default="{ row }"><el-tag :type="row.status === 'PENDING' ? 'warning' : 'info'">{{ todoStatuses[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="remindDate" label="提醒日期" width="120" />

        <el-table-column label="操作" width="230" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="todoDetail = row">详情</el-button><el-button v-if="row.target" link type="primary" @click="openBusiness(row)">去处理</el-button><el-button v-if="row.status === 'PENDING' && !row.readAt" link :disabled="acting" @click="markRead(row)">标记已读</el-button></template></el-table-column>
      </template>
      <template v-else-if="tab === 'operations'">
        <el-table-column label="追溯" width="195"><template #default="{row}"><el-button link type="primary" @click="showOperation(row)">变更对比</el-button><el-button link @click="trace(row)">追溯此对象</el-button></template></el-table-column>
        <el-table-column label="业务" width="100"><template #default="{ row }">{{ bizTypes[row.bizType] || row.bizType }}</template></el-table-column>

        <el-table-column prop="reason" label="操作说明" min-width="270" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" min-width="125" />
        <el-table-column label="操作时间" min-width="175"><template #default="{ row }">{{ time(row.occurredAt) }}</template></el-table-column>
      </template>
      <template v-else>
        <el-table-column type="expand"><template #default="{ row }"><div class="execution-detail"><p>逻辑批次：{{ row.logicalRunId }}</p><p>失败业务编号：{{ failedIds(row).join('、') || '无' }}</p><p>说明：{{ row.errorSummary || '无异常摘要' }}</p><p>结束时间：{{ time(row.finishedAt) }}</p></div></template></el-table-column>
        <el-table-column label="扫描任务" min-width="175"><template #default="{ row }">{{ jobs[row.jobName] || row.jobName }}</template></el-table-column>
        <el-table-column prop="businessDate" label="业务日期" width="120" />
        <el-table-column prop="attemptNo" label="尝试" width="70" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.runStatus === 'SUCCESS' ? 'success' : row.runStatus === 'RUNNING' ? 'info' : 'warning'">{{ runStatuses[row.runStatus] || row.runStatus }}</el-tag></template></el-table-column>
        <el-table-column prop="successCount" label="成功项" width="85" />
        <el-table-column prop="failureCount" label="失败项" width="85" />
        <el-table-column prop="triggeredByName" label="触发人" min-width="125" />
        <el-table-column label="开始时间" min-width="175"><template #default="{ row }">{{ time(row.startedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="125" fixed="right"><template #default="{ row }"><el-button v-if="row.retryable" link type="primary" :disabled="acting" @click="run(row)">补跑失败项</el-button></template></el-table-column>
      </template>
    </DataTable>
<el-drawer :model-value="Boolean(todoDetail)" @close="todoDetail = null" title="待办详情" size="min(620px, 100%)"><template v-if="todoDetail"><DetailSection title="需要处理的事项" emphasis><h3>{{ todoDetail.title }}</h3><p>{{ todoDetail.target?.label || '关联对象不可访问' }}</p><el-tag>{{ todoStatuses[todoDetail.status] }}</el-tag></DetailSection><DetailSection title="提醒与阅读"><el-descriptions :column="1" border><el-descriptions-item label="提醒类型">{{ types[todoDetail.type] || todoDetail.type }}</el-descriptions-item><el-descriptions-item label="提醒日期">{{ todoDetail.remindDate }}</el-descriptions-item><el-descriptions-item label="生成时间">{{ time(todoDetail.createdAt) }}</el-descriptions-item><el-descriptions-item label="阅读时间">{{ time(todoDetail.readAt) }}</el-descriptions-item></el-descriptions></DetailSection><el-button v-if="todoDetail.target" type="primary" @click="openBusiness(todoDetail)">去处理</el-button><p class="scope-note">已读不等于处理完成，实际业务解除后系统才会更新状态。</p></template></el-drawer>
    <el-dialog v-model="operationOpen" title="操作详情与字段变更" width="760px" style="max-width:calc(100vw - 24px)">
<DetailSection v-if="operationSummary" title="操作摘要" emphasis><el-descriptions :column="1" border><el-descriptions-item label="业务 / 对象编号">{{ bizTypes[operationSummary.bizType] }} / {{ operationSummary.bizId }}</el-descriptions-item><el-descriptions-item label="操作说明">{{ operationSummary.reason || '未填写' }}</el-descriptions-item><el-descriptions-item label="操作人 / 时间">{{ operationSummary.operatorName }} / {{ time(operationSummary.occurredAt) }}</el-descriptions-item><el-descriptions-item label="操作编码">{{ operationSummary.actionCode }}</el-descriptions-item></el-descriptions></DetailSection><h3>字段变化</h3>      <div v-loading="operationLoading"><el-alert v-if="operationError" type="error" :title="operationError" :closable="false" /><p class="scope-note">{{ operation?.note }}</p><PagedItems :items="operation?.changes || []"><template #default="{rows:changes}"><el-table :data="changes" empty-text="没有可展示的字段差异"><el-table-column prop="field" label="字段" min-width="145" /><el-table-column prop="before" label="修改前" min-width="220" /><el-table-column prop="after" label="修改后" min-width="220" /></el-table></template></PagedItems></div>
    </el-dialog>
  </div>
</template>
<style scoped>
.task-panel { padding: 16px 20px; margin-bottom: 18px; }
.filters :deep(.el-select) { width: 175px; }
.filters :deep(.el-date-editor) { width: 160px; }
.scope-note { color: var(--crm-text-secondary); font-size: 13px; line-height: 1.7; margin: 5px 0; }
.run-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.run-toolbar > .el-select { width: 195px; }
.execution-detail { padding: 10px 24px; overflow-wrap: anywhere; }
@media (max-width: 600px) { .task-panel { padding: 12px; } .filters :deep(.el-form-item) { margin-right: 10px; } }
</style>
