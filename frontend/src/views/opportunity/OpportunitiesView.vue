<script setup>
import PagedItems from '@/components/PagedItems.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElSelect, ElOption, ElDialog, ElDescriptions,
  ElDescriptionsItem, ElDivider, ElTabs, ElTabPane, ElTimeline, ElTimelineItem,
  ElEmpty, ElInputNumber, ElDatePicker, ElRadioGroup, ElRadio } from 'element-plus'
import { Plus, Search, Refresh, Edit, UserFilled, Document, Clock } from '@element-plus/icons-vue'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import { useAuthStore } from '@/store/auth'
import { listCustomers, listCustomerOwners } from '@/api/customers'
import * as api from '@/api/opportunities'
import * as contractApi from '@/api/contracts'

const auth = useAuthStore()
const admin = computed(() => auth.user?.roleCode === 'ADMIN')
const router = useRouter()
const rows = ref([]), total = ref(0), page = ref(1), size = ref(10), loading = ref(false), failure = ref('')
const filters = reactive({ keyword: '', stageCode: '', activityStatus: '' })
const detail = ref(null), detailOpen = ref(false), detailLoading = ref(false), dialog = ref(''), saving = ref(false), formError = ref('')
const customers = ref([]), members = ref([]), customerLoading = ref(false), tab = ref('overview')
const form = reactive({})
const owners = ref([]), customerPage = ref(1), customerTotal = ref(0), customerKeyword = ref('')
let customerRequest = 0
const ownerLabel = (owner) => `${owner.realName} · ${owner.roleCode === 'MANAGER' ? '经理' : '专员'} · ${owner.teamName}`
const labels = { NEW: '线索', FOLLOWING: '跟进', QUOTED: '报价', DEAL: '成交', WON: '赢单', LOST: '输单', CLOSED: '关闭' }
const activityLabels = { ACTIVE: '活跃', STAGNANT: '停滞', CLOSE_PENDING: '待关闭', TERMINATED: '已结束' }
const resultLabels = { NEED_CONFIRMED: '需求已确认', SOLUTION_DISCUSSED: '方案已沟通', NEXT_STEP_AGREED: '约定下一步', NO_RESPONSE: '无回应', NOT_CONNECTED: '未接通', OTHER: '其他实质结果' }
const reviewLabels = { PENDING: '待查验', CONFIRMED: '已确认', RETURNED: '已打回', NOT_REQUIRED: '仅留痕' }
const active = computed(() => ['NEW', 'FOLLOWING', 'QUOTED', 'DEAL'].includes(detail.value?.stageCode))
const mainStages = computed(() => detail.value?.stageProgress?.filter(s => !['LOST', 'CLOSED'].includes(s.stageCode)) || [])
const terminalStages = computed(() => detail.value?.stageProgress?.filter(s => ['LOST', 'CLOSED'].includes(s.stageCode)) || [])
const currentProgress = computed(() => detail.value?.stageProgress?.find(s => s.current))
const targets = computed(() => {
  const order = ['NEW', 'FOLLOWING', 'QUOTED', 'DEAL', 'WON']
  const index = order.indexOf(detail.value?.stageCode)
  if (!active.value) return []
  return detail.value?.canChangeStage ? [order[index - 1], order[index + 1], 'LOST', 'CLOSED'].filter(Boolean) : []
})
const dialogTitle = computed(() => ({ create: '新建商机', edit: '编辑商机', stage: '管理员修正阶段', threshold: '当前商机阈值', collaborator: '添加协作人', QUOTE: '录入报价', CONFIRMATION: '客户合作确认', follow: '新增商机跟进', decision: '待关闭裁决' })[dialog.value])
const money = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const time = (value) => value ? value.replace('T', ' ') : '暂无'

async function load() {
  loading.value = true; failure.value = ''
  try {
    const result = await api.listOpportunities({ page: page.value, size: size.value, keyword: filters.keyword.trim() || undefined, stageCode: filters.stageCode || undefined, activityStatus: filters.activityStatus || undefined })
    rows.value = result.list; total.value = result.total
  } catch (error) { failure.value = error.message }
  finally { loading.value = false }
}
function search() { if (page.value !== 1) page.value = 1; else load() }
watch([page, size], load)
async function openDetail(row) {
  detailLoading.value = true; detailOpen.value = true
  try { detail.value = await api.getOpportunity(row.id) }
  catch (error) { ElMessage.error(error.message); detailOpen.value = false }
  finally { detailLoading.value = false }
}
async function loadCustomers(keyword = customerKeyword.value, targetPage = 1) {
  const request = ++customerRequest
  customerKeyword.value = keyword; customerPage.value = targetPage; form.customerId = null
  if (!form.ownerId) { customers.value = []; customerTotal.value = 0; customerLoading.value = false; return }
  customerLoading.value = true
  try {
    const result = await listCustomers({ page: targetPage, size: 10, sea: 'PRIVATE', opportunityEligible: true, ownerId: form.ownerId, keyword: keyword || undefined })
    if (request === customerRequest) { customers.value = result.list; customerTotal.value = result.total }
  } catch (error) { if (request === customerRequest) { customers.value = []; customerTotal.value = 0; formError.value = error.message } }
  finally { if (request === customerRequest) customerLoading.value = false }
}
async function openDialog(mode) {
  if (mode === 'create' && !admin.value) return
  dialog.value = mode; formError.value = ''
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, { version: detail.value?.version })
  if (mode === 'create') {
    Object.assign(form, { ownerId: null, customerId: null, opportunityName: '', expectedAmount: 0, expectedCloseDate: '' })
    await loadCustomers('')
    try { owners.value = await listCustomerOwners() } catch (error) { formError.value = error.message }
  }
  if (mode === 'edit') Object.assign(form, { opportunityName: detail.value.opportunityName, expectedAmount: Number(detail.value.expectedAmount), expectedCloseDate: detail.value.expectedCloseDate })
  if (mode === 'stage') Object.assign(form, { targetStage: '', reason: '' })
  if (mode === 'threshold') Object.assign(form, { stagnationDays: detail.value.stagnationDays, graceDays: detail.value.graceDays })
  if (mode === 'collaborator') {
    Object.assign(form, { userId: null, splitRatio: 10 })
    try { const all = await api.listOpportunityMembers(detail.value.id); members.value = all.filter(u => u.id !== detail.value.ownerId && !detail.value.collaborators.some(c => c.userId === u.id)) }
    catch (error) { formError.value = error.message }
  }
  if (['QUOTE', 'CONFIRMATION'].includes(mode)) Object.assign(form, { kind: mode, quoteAmount: Number(detail.value.quoteAmount ?? detail.value.expectedAmount), description: '' })
  if (mode === 'decision') Object.assign(form, { close: false, reason: '' })
  if (mode === 'follow') Object.assign(form, { customerVersion: detail.value.customerVersion, contactId: detail.value.contacts[0]?.id, followMethod: 'PHONE', resultCode: 'NEED_CONFIRMED', content: '', substantiveResult: '', nextAction: '', nextActionAt: '', followedAt: localDateTime(new Date()) })
}
function localDateTime(date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19) }
function validation() {
  if (['create', 'edit'].includes(dialog.value)) {
    if ((dialog.value === 'create' && (!form.ownerId || !form.customerId)) || !form.opportunityName?.trim() || !form.expectedCloseDate || form.expectedAmount == null || form.expectedAmount < 0) return '请完整填写负责人、客户、名称、金额和预计成交日期'
  }
  if (dialog.value === 'stage') {
    if (!form.targetStage) return '请选择目标阶段'
    if (!form.reason?.trim()) return '管理员修正阶段必须填写原因，并仍须满足业务门槛'
  }
  if (dialog.value === 'collaborator' && !form.userId) return '请选择协作人'
  if (['QUOTE', 'CONFIRMATION'].includes(dialog.value) && !form.description?.trim()) return '请填写记录说明'
  if (dialog.value === 'QUOTE' && (form.quoteAmount == null || !Number.isFinite(form.quoteAmount) || form.quoteAmount < 0)) return '请填写有效的报价金额（不得小于 0）'
  if (dialog.value === 'decision' && !form.reason?.trim()) return '请填写裁决原因'
  if (dialog.value === 'follow' && ['contactId', 'content', 'substantiveResult', 'nextAction', 'nextActionAt', 'followedAt'].some(k => !form[k])) return '请完整填写跟进信息'
}
async function save() {
  formError.value = validation() || ''; if (formError.value) return
  saving.value = true
  const mode = dialog.value, id = detail.value?.id, previousStage = detail.value?.stageCode
  try {
    const payload = { ...form }
    if (mode === 'create') await api.createOpportunity(payload)
    else if (mode === 'edit') await api.editOpportunity(id, payload)
    else if (mode === 'stage') await api.changeOpportunityStage(id, payload)
    else if (mode === 'threshold') await api.updateOpportunityThreshold(id, payload)
    else if (mode === 'collaborator') await api.addOpportunityCollaborator(id, payload)
    else if (mode === 'follow') await api.addOpportunityFollow(id, payload)
    else if (mode === 'decision') await api.decideOpportunityClose(id, payload)
    else await api.recordOpportunityEvidence(id, payload)
    dialog.value = ''; await load()
    if (mode !== 'create') await openDetail({ id })
    if (['follow', 'QUOTE', 'CONFIRMATION'].includes(mode) && detailOpen.value && detail.value?.stageCode !== previousStage) {
      ElMessage.success(`保存成功，已从「${labels[previousStage]}」自动推进至「${labels[detail.value.stageCode]}」`)
    } else if (mode === 'QUOTE') {
      ElMessage.success('报价已保存，请查看当前阶段及必需要求')
    } else ElMessage.success('保存成功')
  } catch (error) { formError.value = error.message }
  finally { saving.value = false }
}
async function removeCollaborator(row) {
  try {
    await ElMessageBox.confirm(`确定移除协作人“${row.realName}”吗？`, '移除协作人', { type: 'warning' })
    await api.removeOpportunityCollaborator(detail.value.id, row.id, detail.value.version)
    ElMessage.success('协作人已移除'); await openDetail(detail.value); await load()
  } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message) }
}
async function createContract() {
  try {
    await ElMessageBox.confirm('将根据当前成交商机创建合同登记草稿，不会生成合同正文。是否继续？', '创建合同登记', { type: 'info' })
    const id = await contractApi.createContractFromOpportunity(detail.value.id)
    ElMessage.success('合同登记草稿已创建')
    router.push({ path: '/contracts', query: { id } })
  } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message) }
}
async function scan() {
  try {
    await ElMessageBox.confirm('按当前阈值重新计算商机活跃度并生成提醒，是否执行？', '扫描商机', { type: 'warning' })
    loading.value = true
    const result = await api.scanOpportunityActivity()
    if (result.failedIds.length) ElMessage.warning(`已处理 ${result.processed} 条，失败 ${result.failedIds.length} 条`)
    else ElMessage.success(`已处理 ${result.processed} 条，状态变化 ${result.changed} 条`)
    await load()
  } catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message) }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageHeading title="商机管理" eyebrow="OPPORTUNITY">
      <el-button v-if="auth.user?.roleCode === 'ADMIN'" :icon="Clock" :disabled="loading" @click="scan">活跃度扫描</el-button>
      <el-button v-if="admin" type="primary" :icon="Plus" @click="openDialog('create')">新建商机</el-button>
    </PageHeading>
    <form class="panel toolbar" @submit.prevent="search">
      <el-input v-model="filters.keyword" aria-label="搜索商机或客户" placeholder="商机或客户名称" clearable />
      <el-select v-model="filters.stageCode" aria-label="商机阶段" placeholder="全部阶段" clearable><el-option v-for="(label, code) in labels" :key="code" :value="code" :label="label" /></el-select>
      <el-select v-model="filters.activityStatus" aria-label="活跃度" placeholder="全部活跃度" clearable><el-option v-for="(label, code) in activityLabels" :key="code" :value="code" :label="label" /></el-select>
      <el-button type="primary" :icon="Search" native-type="submit" :loading="loading">查询</el-button>
      <el-button :icon="Refresh" aria-label="刷新列表" title="刷新列表" @click="load" />
    </form>
    <DataTable :rows="rows" :total="total" :loading="loading" :error="failure" v-model:page="page" @retry="load">
      <el-table-column prop="opportunityName" label="商机名称" min-width="190" />
      <el-table-column prop="customerName" label="客户" min-width="150" />
      <el-table-column label="所属负责人 / 团队" min-width="170"><template #default="{ row }"><div>{{ row.ownerName }}</div><div class="scope-note">{{ row.teamName }}</div></template></el-table-column>
      <el-table-column label="阶段" width="90"><template #default="{ row }"><el-tag size="small">{{ labels[row.stageCode] }}</el-tag></template></el-table-column>
      <el-table-column label="活跃度" width="100"><template #default="{ row }"><el-tag size="small" :type="row.activityStatus === 'ACTIVE' ? 'success' : row.activityStatus === 'CLOSE_PENDING' ? 'danger' : 'info'">{{ activityLabels[row.activityStatus] }}</el-tag></template></el-table-column>
      <el-table-column label="预计金额（元）" align="right" min-width="140"><template #default="{ row }">{{ money(row.expectedAmount) }}</template></el-table-column>
      <el-table-column prop="expectedCloseDate" label="预计成交日" width="125" />
      <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
    </DataTable>

    <el-drawer v-model="detailOpen" title="商机详情" size="min(860px, 100%)">
      <div v-if="detailLoading" v-loading="true" class="loading" />
      <template v-else-if="detail">
        <div class="detail-head"><div><h2>{{ detail.opportunityName }}</h2><p>{{ detail.customerName }} · 所属负责人：{{ detail.ownerName }} · 所属团队：{{ detail.teamName }}</p></div><el-tag>{{ labels[detail.stageCode] }}</el-tag></div>
        <section class="stage-panel" aria-label="商机阶段与推进要求">
          <div class="stage-title"><strong>当前阶段：{{ labels[detail.stageCode] }}</strong><span>业务记录满足要求后，系统自动推进</span></div>
          <ol class="stage-track">
            <li v-for="stage in mainStages" :key="stage.stageCode" :class="{ current: stage.current, passed: stage.status === 'PASSED' }" :aria-current="stage.current ? 'step' : undefined">
              <span class="stage-number">{{ mainStages.indexOf(stage) + 1 }}</span><strong>{{ stage.stageName }}</strong>
              <span class="stage-badge">{{ stage.current ? '当前阶段' : stage.status === 'PASSED' ? '已走过' : '后续阶段' }}</span>
            </li>
          </ol>
          <div class="terminal-stages"><span>结束分支：</span><el-tag v-for="stage in terminalStages" :key="stage.stageCode" :type="stage.current ? 'danger' : 'info'" :effect="stage.current ? 'dark' : 'plain'">{{ stage.stageName }}{{ stage.current ? ' · 当前阶段' : '' }}</el-tag><span>仅管理员可修正为输单／关闭，须填写原因并通过合同与回款校验</span></div>
          <p class="stage-description">{{ currentProgress?.description }}</p>
          <template v-if="currentProgress?.nextStage">
            <h3>距下一阶段「{{ currentProgress.nextStageName }}」的必需要求</h3>
            <ul class="stage-requirements">
              <li v-for="requirement in currentProgress.requirements" :key="requirement.code">
                <el-tag size="small" :type="requirement.met ? 'success' : 'warning'">{{ requirement.met ? '已完成' : '未完成' }}</el-tag>
                <div><strong>{{ requirement.label }}</strong><p>{{ requirement.action }}</p>
                  <p v-if="requirement.code === 'QUOTE' && !requirement.met">请使用下方「录入报价」填写金额和报价说明；编辑预计合同总额或填写跟进内容不会登记报价。</p>
                  <el-button v-if="requirement.code === 'QUOTE' && !requirement.met && detail.canEdit && active && detail.stageCode !== 'NEW'" type="primary" size="small" @click="openDialog('QUOTE')">去录入报价</el-button>
                </div>
              </li>
            </ul>
            <el-alert v-if="currentProgress.ready" title="必需条件已满足；下一次相关业务记录成功保存时，系统自动核对并推进。已有数据不会因查询而改阶段。" type="info" :closable="false" />
            <p class="stage-note">待查验跟进暂时计入；打回后撤销有效性并重算计时，保留阶段历史，缺项未补齐前不能继续推进。</p>
          </template>
          <p v-else class="stage-note">当前已处于结束阶段；保留业务历史，不再自动推进。赢单后的履约及回款仍需继续处理。</p>
          <details class="all-stage-rules"><summary>查看所有阶段的必需要求及完成情况</summary>
            <article v-for="stage in mainStages.filter(s => s.nextStage)" :key="stage.stageCode">
              <h4>{{ stage.stageName }} → {{ stage.nextStageName }}{{ stage.current ? '（当前）' : '' }}</h4>
              <ul class="stage-requirements"><li v-for="requirement in stage.requirements" :key="requirement.code"><el-tag size="small" :type="requirement.met ? 'success' : 'warning'">{{ requirement.met ? '已完成' : '未完成' }}</el-tag><span>{{ requirement.label }}</span></li></ul>
            </article>
          </details>
        </section>
        <el-alert v-if="detail.activityStatus === 'CLOSE_PENDING'" title="待关闭裁决中，继续保持客户回收保护" type="warning" :closable="false" show-icon />
        <div class="actions" v-if="active">
          <el-button v-if="detail.canEdit" :icon="Edit" @click="openDialog('edit')">编辑</el-button>
          <el-button v-if="detail.canEdit && detail.stageCode !== 'NEW'" type="primary" :icon="Document" @click="openDialog('QUOTE')">录入报价</el-button>
          <el-button v-if="detail.canEdit && ['QUOTED', 'DEAL'].includes(detail.stageCode)" :icon="Document" @click="openDialog('CONFIRMATION')">客户合作确认</el-button>
          <el-button v-if="detail.canChangeStage" @click="openDialog('stage')">管理员修正阶段</el-button>
          <el-button v-if="detail.canEdit && detail.stageCode === 'DEAL'" :icon="Document" @click="createContract">创建合同登记</el-button>
          <el-button :icon="Plus" @click="openDialog('follow')">跟进</el-button>
          <el-button v-if="detail.canSetThreshold" :icon="Clock" @click="openDialog('threshold')">停滞阈值</el-button>
          <el-button v-if="detail.canManageCollaborators" :icon="UserFilled" @click="openDialog('collaborator')">协作人</el-button>
          <el-button v-if="detail.canDecidePending" type="warning" @click="openDialog('decision')">待关闭裁决</el-button>
        </div>
        <el-tabs v-model="tab">
          <el-tab-pane name="overview" label="概览与资料">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="阶段 / 活跃度">{{ labels[detail.stageCode] }} / {{ activityLabels[detail.activityStatus] }}</el-descriptions-item>
              <el-descriptions-item label="预计合同总额">¥ {{ money(detail.expectedAmount) }}</el-descriptions-item>
              <el-descriptions-item label="预计成交日期">{{ detail.expectedCloseDate || '尚未补录' }}</el-descriptions-item>
              <el-descriptions-item label="停滞阈值">{{ detail.stagnationDays }} 天 + 宽限 {{ detail.graceDays }} 天</el-descriptions-item>
              <el-descriptions-item label="最近有效推进">{{ time(detail.lastEffectiveProgressAt) }}</el-descriptions-item>
              <el-descriptions-item label="报价">{{ detail.quoteAt ? `¥ ${money(detail.quoteAmount)} · ${time(detail.quoteAt)}` : '暂无报价记录' }}</el-descriptions-item>
              <el-descriptions-item label="客户确认">{{ detail.customerConfirmAt ? time(detail.customerConfirmAt) : '暂无合作确认' }}</el-descriptions-item>
            </el-descriptions>
            <el-divider content-position="left">协作人</el-divider>
            <PagedItems :items="detail.collaborators" v-slot="{ rows: pageRows }"><el-table :data="pageRows" empty-text="暂无协作人"><el-table-column prop="realName" label="姓名" /><el-table-column label="分成比例"><template #default="{ row }">{{ row.splitRatio }}%</template></el-table-column><el-table-column v-if="detail.canManageCollaborators && active" label="操作" width="80"><template #default="{ row }"><el-button link type="danger" @click="removeCollaborator(row)">移除</el-button></template></el-table-column></el-table></PagedItems>
          </el-tab-pane>
          <el-tab-pane name="follow" label="商机跟进"><PagedItems :items="detail.followRecords" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="time(item.followedAt)"><strong>{{ resultLabels[item.resultCode] }} · {{ reviewLabels[item.reviewStatus] }}</strong><p>{{ item.content }}</p><p>{{ item.substantiveResult }} · {{ item.followUserName }}</p><p>下一步：{{ item.nextAction }} / {{ time(item.nextActionAt) }}</p><p v-if="item.reviewReason">查验意见：{{ item.reviewReason }}</p></el-timeline-item></el-timeline></PagedItems><el-empty v-if="!detail.followRecords?.length" description="暂无商机跟进" /></el-tab-pane>
          <el-tab-pane name="history" label="阶段历史"><PagedItems :items="detail.stageHistory" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="time(item.occurredAt)"><strong>{{ labels[item.fromStage] || '创建' }} → {{ labels[item.toStage] }}</strong><p>{{ item.reason || '阶段推进' }} · {{ item.operatorName }}</p></el-timeline-item></el-timeline></PagedItems></el-tab-pane>
          <el-tab-pane v-if="detail.canEdit" name="audit" label="操作记录"><PagedItems :items="detail.operationHistory" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="time(item.occurredAt)"><strong>{{ item.reason || item.actionCode }}</strong><p>操作人：{{ item.operatorId || '系统任务' }}</p><details><summary>变更明细</summary><pre>{{ item.beforeJson }}</pre><pre>{{ item.afterJson }}</pre></details></el-timeline-item></el-timeline></PagedItems></el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog :model-value="Boolean(dialog)" @update:model-value="value => { if (!value) dialog = '' }" :title="dialogTitle" width="min(560px, 94%)" destroy-on-close :close-on-click-modal="!saving">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="form-error" />
      <el-form label-position="top" @submit.prevent="save">
        <template v-if="['create', 'edit'].includes(dialog)">
          <el-alert v-if="dialog === 'edit'" title="这里修改的是预计合同总额。正式报价请使用商机详情中的「录入报价」，保存金额和说明后由系统自动判定阶段。" type="info" :closable="false" show-icon />
          <template v-if="dialog === 'create'">
            <el-form-item label="私海负责人（经理 / 专员）" required><el-select v-model="form.ownerId" filterable placeholder="选择负责此商机的经理或专员" @change="loadCustomers('')"><el-option v-for="owner in owners" :key="owner.id" :value="owner.id" :label="ownerLabel(owner)" /></el-select></el-form-item>
            <el-form-item label="该负责人名下的私海客户（跟进中 / 保护中）" required>
              <el-select v-model="form.customerId" filterable remote :disabled="!form.ownerId" :remote-method="loadCustomers" :loading="customerLoading" placeholder="搜索客户" no-data-text="暂无符合条件的客户，请确认归属及首次有效跟进">
                <el-option v-for="item in customers" :key="item.id" :value="item.id" :label="`${item.customerName} · ${item.ownerName} · ${item.teamName}`" />
                <template #footer><el-pagination :current-page="customerPage" :total="customerTotal" :page-size="10" :pager-count="5" layout="total, prev, next" @update:current-page="value => loadCustomers(customerKeyword, value)" /></template>
              </el-select>
            </el-form-item>
            <p class="stage-note">新商机归属所选客户的负责人及团队；客户尚未首次有效跟进时，先完成跟进再创建商机。</p>
          </template>
          <el-form-item label="商机名称" required><el-input v-model="form.opportunityName" maxlength="160" /></el-form-item>
          <el-form-item label="预计合同总额（元）" required><el-input-number v-model="form.expectedAmount" :min="0" :precision="2" controls-position="right" /></el-form-item>
          <el-form-item label="预计成交日期" required><el-date-picker v-model="form.expectedCloseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        </template>
        <template v-else-if="dialog === 'stage'"><el-alert title="日常阶段由系统自动推进。管理员修正仍须相邻流转、满足业务门槛并填写原因。" type="info" :closable="false" /><el-form-item label="目标阶段" required><el-select v-model="form.targetStage"><el-option v-for="stage in targets" :key="stage" :value="stage" :label="labels[stage]" /></el-select></el-form-item><el-form-item label="修正原因" required><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item></template>
        <template v-else-if="dialog === 'threshold'"><el-form-item label="停滞天数"><el-input-number v-model="form.stagnationDays" :min="1" :max="3650" /></el-form-item><el-form-item label="宽限天数"><el-input-number v-model="form.graceDays" :min="0" :max="365" /></el-form-item></template>
        <template v-else-if="dialog === 'collaborator'"><el-form-item label="同团队专员" required><el-select v-model="form.userId" filterable><el-option v-for="item in members" :key="item.id" :value="item.id" :label="`${item.realName} · ${item.username}`" /></el-select></el-form-item><el-form-item label="分成比例（%，合计最多30）"><el-input-number v-model="form.splitRatio" :min="0.01" :max="30" :precision="2" /></el-form-item></template>
        <template v-else-if="['QUOTE', 'CONFIRMATION'].includes(dialog)">
          <el-alert v-if="dialog === 'QUOTE'" title="金额默认带入已有报价或预计合同总额，请核对并填写报价说明。存在本商机有效跟进时，保存报价将自动推进阶段，无需手动切换。" type="info" :closable="false" show-icon />
          <el-form-item v-if="dialog === 'QUOTE'" label="报价金额（元）" required><el-input-number v-model="form.quoteAmount" :min="0" :precision="2" /></el-form-item>
          <el-form-item :label="dialog === 'QUOTE' ? '报价说明' : '记录说明'" required><el-input v-model="form.description" type="textarea" maxlength="500" show-word-limit :placeholder="dialog === 'QUOTE' ? '填写本次报价的产品或服务范围、金额依据、沟通情况等' : '填写客户合作确认的具体情况'" /></el-form-item>
        </template>
        <template v-else-if="dialog === 'decision'"><el-form-item label="裁决结果"><el-radio-group v-model="form.close"><el-radio :value="false">继续推进</el-radio><el-radio v-if="detail.canChangeStage" :value="true">确认关闭</el-radio></el-radio-group><p v-if="!detail.canChangeStage" class="stage-note">经理可解除待关闭状态；确认关闭由管理员操作。</p></el-form-item><el-form-item label="裁决原因" required><el-input v-model="form.reason" type="textarea" maxlength="500" /></el-form-item></template>
        <template v-else-if="dialog === 'follow'">
          <el-form-item label="联系人" required><el-select v-model="form.contactId"><el-option v-for="c in detail.contacts" :key="c.id" :value="c.id" :label="`${c.contactName} · ${c.phone}`" /></el-select></el-form-item>
          <el-form-item label="沟通方式"><el-select v-model="form.followMethod"><el-option v-for="(label, value) in { PHONE: '电话', WECHAT: '微信', VISIT: '拜访', EMAIL: '邮件', OTHER: '其他' }" :key="value" :value="value" :label="label" /></el-select></el-form-item>
          <el-form-item label="跟进时间" required><el-date-picker v-model="form.followedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
          <el-form-item label="沟通原文" required><el-input v-model="form.content" type="textarea" maxlength="1000" /></el-form-item>
          <el-form-item label="沟通结果"><el-select v-model="form.resultCode"><el-option v-for="(label, code) in resultLabels" :key="code" :value="code" :label="label" /></el-select></el-form-item>
          <el-form-item label="具体结果" required><el-input v-model="form.substantiveResult" type="textarea" maxlength="500" /></el-form-item>
          <el-form-item label="下一步动作" required><el-input v-model="form.nextAction" maxlength="500" /></el-form-item>
          <el-form-item label="下次跟进时间" required><el-date-picker v-model="form.nextActionAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = ''">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display:flex;gap:12px;padding:18px;margin-bottom:18px;flex-wrap:wrap; }
.toolbar .el-input { width:240px; }.toolbar .el-select { width:150px; }
.loading { min-height:250px; }.detail-head { display:flex;justify-content:space-between;gap:20px;align-items:start;margin-bottom:18px; }
.detail-head h2 { margin:0 0 6px;font-size:20px;overflow-wrap:anywhere; }.detail-head p { color:var(--crm-text-secondary);font-size:13px; }
.actions { display:flex;gap:8px;flex-wrap:wrap;margin:16px 0; }.actions .el-button { margin:0; }
.el-select,.el-date-editor,.el-input-number { width:100%; }.form-error { margin-bottom:16px; }
.el-timeline { padding:12px 8px 0 16px; }.el-timeline p { color:var(--crm-text-secondary);font-size:13px;overflow-wrap:anywhere; }
pre { white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px; }.el-divider { margin:24px 0 16px; }
.stage-panel { margin:18px 0;padding:18px;border:1px solid #dce5f1;border-radius:12px;background:#f8faff; }
.stage-title { display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;color:#123b70; }.stage-title span,.stage-note,.stage-description { color:var(--crm-text-secondary);font-size:13px;line-height:1.7; }
.stage-track { display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;padding:0;list-style:none;margin:18px 0; }
.stage-track li { display:flex;align-items:center;flex-direction:column;gap:7px;padding:12px 4px;border:1px solid #dce3ed;border-radius:8px;background:white;color:#68788f; }
.stage-track li.current { border:2px solid #2563eb;background:#eaf2ff;color:#123f98;box-shadow:0 0 0 3px #dce9ff; }.stage-track li.passed { background:#edf7f1;color:#28724d;border-color:#c6e5d2; }
.stage-number { display:grid;place-items:center;border-radius:50%;width:25px;height:25px;background:#edf0f5; }.current .stage-number { background:#2563eb;color:white; }.stage-badge { font-size:12px; }
.terminal-stages { display:flex;gap:8px;align-items:center;flex-wrap:wrap;color:var(--crm-text-secondary);font-size:12px; }.stage-panel h3 { font-size:15px;margin:20px 0 12px; }
.stage-requirements { list-style:none;padding:0;display:grid;gap:12px; }.stage-requirements li { display:flex;gap:10px;align-items:flex-start;font-size:13px;line-height:1.6; }.stage-requirements .el-tag { flex-shrink:0; }.stage-requirements p { margin:3px 0 0;color:var(--crm-text-secondary); }.all-stage-rules summary { cursor:pointer;color:#245ac2;font-size:13px;padding:8px 0; }.all-stage-rules h4 { font-size:13px; }
@media(max-width:600px){.stage-panel{padding:12px;}.stage-track{gap:5px;}.stage-track li{padding:10px 2px;}.stage-badge{font-size:10px;}.stage-track strong{font-size:13px;}}
@media(max-width:600px){.toolbar .el-input,.toolbar .el-select{width:100%;}.toolbar{padding:14px;}.detail-head{gap:10px;}}
</style>
