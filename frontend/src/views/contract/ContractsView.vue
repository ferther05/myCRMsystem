<script setup>
import PagedItems from '@/components/PagedItems.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElAlert, ElButton, ElDatePicker, ElDescriptions, ElDescriptionsItem, ElDialog, ElDivider, ElDrawer, ElEmpty, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable, ElTableColumn, ElTag, ElTimeline, ElTimelineItem, ElTabs, ElTabPane, ElSteps, ElStep } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import * as api from '@/api/contracts'

const route = useRoute()
const rows = ref([]), total = ref(0), page = ref(1), size = ref(10), loading = ref(false), failure = ref('')
const filters = reactive({ keyword: '', contractStatus: '', signingProgress: '' })
const listMode = ref('registration'), acting = ref(false)
const detail = ref(null), detailOpen = ref(false), detailLoading = ref(false), dialog = ref(''), saving = ref(false), formError = ref(''), selectedFile = ref(null), progressDialog = ref(false)
const form = reactive({})
const statusLabels = { DRAFT: '草稿', PENDING_REVIEW: '待经理核验', RETURNED: '退回补充', SIGNED: '已签约登记', PERFORMING: '履行中', SETTLED: '已结清', TERMINATED: '已终止', VOID: '已撤回' }
const progressLabels = { INTERNAL_HANDLING: '内部办理', WAIT_OUR_SIGN: '待我方签署', WAIT_COUNTERPARTY_SIGN: '待对方签署', BOTH_SIGNED: '双方已签署', EXCEPTION: '签署异常', WITHDRAWN: '已撤回' }
const progressOptions = [
  { value: 'INTERNAL_HANDLING', label: '内部办理' }, { value: 'WAIT_OUR_SIGN', label: '待我方签署' },
  { value: 'WAIT_COUNTERPARTY_SIGN', label: '待对方签署' }, { value: 'EXCEPTION', label: '签署异常' }, { value: 'WITHDRAWN', label: '已撤回' },
]
const normalNextProgress = { INTERNAL_HANDLING: ['WAIT_OUR_SIGN', 'EXCEPTION', 'WITHDRAWN'], WAIT_OUR_SIGN: ['WAIT_COUNTERPARTY_SIGN', 'EXCEPTION', 'WITHDRAWN'], WAIT_COUNTERPARTY_SIGN: ['EXCEPTION', 'WITHDRAWN'], EXCEPTION: ['INTERNAL_HANDLING'] }
const progressForm = reactive({ targetProgress: '', reason: '' })
const progressTargets = computed(() => {
  if (!detail.value) return []
  const values = detail.value.canCorrectSigningProgress
    ? progressOptions.map(item => item.value).filter(value => value !== detail.value.signingProgress)
    : (normalNextProgress[detail.value.signingProgress] || [])
  return values.map(value => progressOptions.find(item => item.value === value)).filter(Boolean)
})
const signingStep = computed(() => {
  const index = ['INTERNAL_HANDLING', 'WAIT_OUR_SIGN', 'WAIT_COUNTERPARTY_SIGN', 'BOTH_SIGNED'].indexOf(detail.value?.signingProgress)
  return index === 3 ? 4 : Math.max(0, index)
})
const dialogOpen = computed({ get: () => dialog.value !== '', set: value => { if (!value) { dialog.value = ''; selectedFile.value = null } } })
const filterStatuses = computed(() => listMode.value === 'registration' ? { UNREGISTERED: '未登记', ...statusLabels } : statusLabels)
const money = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const time = (value) => value ? value.replace('T', ' ') : '暂无'
let generation = 0

async function load() {
  const current = ++generation
  const mode = listMode.value
  loading.value = true; failure.value = ''
  try { const fetch = mode === 'registration' ? api.listRegistrationOpportunities : api.listContracts; const result = await fetch({ page: page.value, size: size.value, keyword: filters.keyword.trim() || undefined, contractStatus: filters.contractStatus || undefined, signingProgress: filters.signingProgress || undefined }); if (current === generation) { rows.value = result.list; total.value = result.total } }
  catch (error) { if (current === generation) { rows.value = []; total.value = 0; failure.value = error.message } }
  finally { if (current === generation) loading.value = false }
}
async function search() { page.value = 1; await load() }
watch([page, size], load)
watch(listMode, () => { filters.contractStatus = ''; filters.signingProgress = ''; if (page.value === 1) void load(); else page.value = 1 })
async function openDetail(rowOrId) {
  detail.value = null; detailOpen.value = true; detailLoading.value = true
  try { detail.value = await api.getContract(typeof rowOrId === 'object' ? rowOrId.id : rowOrId) }
  catch (error) { ElMessage.error(error.message); detailOpen.value = false }
  finally { detailLoading.value = false }
}
async function uploadFromArchive(row) { await openDetail(row); if (detailOpen.value && detail.value?.canEdit) openUpload() }
function openEdit() {
  Object.assign(form, { version: detail.value.version, contractName: detail.value.contractName, externalContractNo: detail.value.externalContractNo || '', businessType: detail.value.businessType || '', ourPartyName: detail.value.ourPartyName || '', counterpartyName: detail.value.counterpartyName || '', contractAmount: Number(detail.value.contractAmount), signedAt: detail.value.signedAt || '', expireDate: detail.value.expireDate || '', description: detail.value.description || '' })
  formError.value = ''; dialog.value = 'edit'
}
function openUpload() { selectedFile.value = null; form.note = ''; formError.value = ''; dialog.value = 'upload' }
async function registrationAction(row, mode) {
  acting.value = true
  try {
    const id = row.contractId || await api.createContractFromOpportunity(row.opportunityId)
    const current = await api.getContract(id)
    detail.value = current; detailOpen.value = true; detailLoading.value = false
    if (current.canEdit) { if (mode === 'upload') openUpload(); else openEdit() }
    await load()
  } catch (error) { ElMessage.error(error.message); await load() }
  finally { acting.value = false }
}
function chooseFile(event) { selectedFile.value = event.target.files?.[0] || null }
async function save() {
  formError.value = ''
  if (dialog.value === 'edit' && (!form.contractName?.trim() || form.contractAmount == null || form.contractAmount < 0)) { formError.value = '请填写合同名称和非负合同金额'; return }
  saving.value = true
  try {
    if (dialog.value === 'edit') await api.editContract(detail.value.id, form)
    if (dialog.value === 'upload') { if (!selectedFile.value) { formError.value = '请选择合同文件'; saving.value = false; return }; const data = new FormData(); data.append('materialType', 'SIGNED_CONTRACT'); data.append('note', form.note || ''); data.append('file', selectedFile.value); await api.uploadContractAttachment(detail.value.id, data) }
    dialog.value = ''; selectedFile.value = null; ElMessage.success('操作成功'); await load(); await openDetail(detail.value.id)
  } catch (error) { formError.value = error.message }
  finally { saving.value = false }
}
async function submit() {
  try { await ElMessageBox.confirm('提交后将锁定登记字段，等待经理核验。是否继续？', '提交合同核验', { type: 'warning' }); await api.submitContract(detail.value.id, { version: detail.value.version }); ElMessage.success('已提交经理核验'); await load(); await openDetail(detail.value.id) }
  catch (error) { if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message) }
}
async function review(pass) {
  const opinion = await ElMessageBox.prompt(pass ? '填写核验意见' : '填写退回原因', pass ? '通过合同核验' : '退回合同资料', { inputValue: pass ? '已核对登记信息与签署资料' : '', inputValidator: value => value?.trim() ? true : '请填写意见' }).catch(() => null)
  if (!opinion) return
  try { await api.reviewContract(detail.value.id, { version: detail.value.version, pass, opinion: opinion.value }); ElMessage.success(pass ? '核验通过，系统已检查商机自动推进条件，可在商机详情查看结果' : '已退回补充'); await load(); await openDetail(detail.value.id) }
  catch (error) { ElMessage.error(error.message) }
}
function openProgress() {
  progressForm.targetProgress = progressTargets.value[0]?.value || ''
  progressForm.reason = ''
  progressDialog.value = true
}
async function saveProgress() {
  if (!progressForm.targetProgress || !progressForm.reason.trim()) return
  saving.value = true
  try {
    await api.updateSigningProgress(detail.value.id, { version: detail.value.version, targetProgress: progressForm.targetProgress, reason: progressForm.reason.trim() })
    progressDialog.value = false
    ElMessage.success('签署进度已更新')
    await load(); await openDetail(detail.value.id)
  } catch (error) { ElMessage.error(error.message) }
  finally { saving.value = false }
}
async function download(item) { try { await api.downloadContractAttachment(detail.value.id, item.id, item.fileName) } catch (error) { ElMessage.error(error.message || '下载失败') } }
onMounted(async () => { await load(); if (route.query.id) await openDetail(Number(route.query.id)) })
</script>

<template>
  <div>
    <PageHeading title="合同登记" eyebrow="CONTRACT" description="登记实际合同信息和签署资料；系统不生成合同正文，也不判断合同法律效力。" />
    <el-tabs v-model="listMode"><el-tab-pane label="成交商单登记" name="registration" /><el-tab-pane label="已登记档案" name="archive" /></el-tabs>
    <el-alert v-if="listMode === 'registration'" title="仅列出当前处于成交阶段的商机。请点击对应商单后的“登记信息”或“上传合同”；核验后赢单的合同可在“已登记档案”查询。" type="info" :closable="false" show-icon class="form-error" />
    <form class="panel toolbar" @submit.prevent="search">
      <el-input v-model="filters.keyword" :placeholder="listMode === 'registration' ? '商单、客户或内部合同编号' : '合同、客户或内部编号'" clearable />
      <el-select v-model="filters.contractStatus" placeholder="全部登记状态" clearable><el-option v-for="(label, code) in filterStatuses" :key="code" :value="code" :label="label" /></el-select>
      <el-select v-model="filters.signingProgress" placeholder="全部签署进度" clearable><el-option v-for="item in progressOptions" :key="item.value" :value="item.value" :label="item.label" /></el-select>
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="load">刷新</el-button>
    </form>
    <DataTable :rows="rows" :total="total" :loading="loading" :error="failure" v-model:page="page" @retry="load">
      <el-table-column v-if="listMode === 'registration'" prop="opportunityName" label="成交商单" min-width="220" />
      <el-table-column v-else prop="contractName" label="合同名称" min-width="200" />
      <el-table-column prop="customerName" label="客户" min-width="170" />
      <el-table-column label="所属负责人 / 团队" min-width="170"><template #default="{ row }"><div>{{ row.ownerName }}</div><div class="scope-note">{{ row.teamName }}</div></template></el-table-column>
      <el-table-column v-if="listMode === 'registration'" label="商机阶段" width="95"><template #default><el-tag size="small" type="success">成交</el-tag></template></el-table-column>
      <el-table-column label="登记状态" width="120"><template #default="{ row }"><el-tag size="small">{{ statusLabels[row.contractStatus] || '未登记' }}</el-tag></template></el-table-column>
      <el-table-column label="签署进度" width="125"><template #default="{ row }">{{ progressLabels[row.signingProgress] || '未开始' }}</template></el-table-column>
      <el-table-column label="内部合同编号" min-width="200"><template #default="{ row }">{{ row.contractNo || '尚未创建登记' }}</template></el-table-column>
      <el-table-column :label="listMode === 'registration' ? '预计金额（元）' : '合同金额（元）'" align="right" min-width="140"><template #default="{ row }">{{ money(listMode === 'registration' ? row.expectedAmount : row.contractAmount) }}</template></el-table-column>
      <el-table-column label="操作" :width="listMode === 'registration' ? 235 : 150" fixed="right"><template #default="{ row }"><template v-if="listMode === 'registration'"><el-button v-if="row.canUpload" link type="primary" :disabled="acting" @click="registrationAction(row, 'edit')">登记信息</el-button><el-button v-if="row.canUpload" link type="primary" :disabled="acting" @click="registrationAction(row, 'upload')">上传合同</el-button><el-button v-if="row.contractId" link type="primary" :disabled="acting" @click="openDetail(row.contractId)">详情</el-button></template><template v-else><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="row.canEdit" link type="primary" @click="uploadFromArchive(row)">上传合同</el-button></template></template></el-table-column>
    </DataTable>
    <p class="scope-note">合同登记支持一条商机一份主合同登记；不同业务领域的具体条款由实际合同文件承载，补充协议以新资料版本归档。</p>

    <el-drawer v-model="detailOpen" title="合同详情" size="min(900px, 100%)">
      <div v-if="detailLoading" v-loading="true" class="loading" />
      <template v-else-if="detail">
        <div class="detail-head"><div><h2>{{ detail.contractName }}</h2><p>{{ detail.customerName }} · {{ detail.opportunityName }} · {{ detail.contractNo }}</p><p>所属负责人：{{ detail.ownerName }} · 所属团队：{{ detail.teamName }}</p></div><el-tag>{{ statusLabels[detail.contractStatus] }}</el-tag></div>
        <el-alert type="info" :closable="false" show-icon title="合同正文由业务人员根据实际交易在线下协商和签署，本页面只登记、归档和核验资料。" />
        <p v-if="detail.canEdit" class="scope-note">提交核验前须补齐双方签约主体、合同金额、实际签订日期，并上传签署资料。草稿可先保存；核验通过且商机前序条件齐全后，系统自动推进赢单。</p>
        <el-divider content-position="left">签署进度</el-divider>
        <el-alert v-if="detail.signingProgress === 'EXCEPTION'" type="warning" :closable="false" title="当前存在签署异常；补正后可回到“内部办理”继续跟踪。" />
        <el-alert v-if="detail.signingProgress === 'WITHDRAWN'" type="info" :closable="false" title="该登记已撤回并终止；如需重新办理，请从成交商机新建登记。" />
        <el-steps v-if="!['EXCEPTION','WITHDRAWN'].includes(detail.signingProgress)" :active="signingStep" finish-status="success" align-center class="signing-steps">
          <el-step title="内部办理" /><el-step title="待我方签署" /><el-step title="待对方签署" /><el-step title="双方已签署" />
        </el-steps>
        <p class="scope-note current-progress">当前签署节点：<strong>{{ progressLabels[detail.signingProgress] || detail.signingProgress }}</strong>；内部核验状态：<strong>{{ statusLabels[detail.contractStatus] || detail.contractStatus }}</strong></p>
        <div class="actions"><el-button v-if="detail.canEdit" @click="openEdit">编辑登记</el-button><el-button v-if="detail.canEdit" @click="openUpload">上传合同</el-button><el-button v-if="detail.canUpdateSigningProgress || detail.canCorrectSigningProgress" @click="openProgress">{{ detail.canCorrectSigningProgress ? '管理员纠正进度' : '更新签署进度' }}</el-button><el-button v-if="detail.canSubmit" type="primary" @click="submit">提交经理核验</el-button><el-button v-if="detail.canReview" type="success" @click="review(true)">核验通过</el-button><el-button v-if="detail.canReview" type="warning" @click="review(false)">退回补充</el-button></div>
        <el-descriptions :column="1" border><el-descriptions-item label="内部编号">{{ detail.contractNo }}</el-descriptions-item><el-descriptions-item label="正式合同编号">{{ detail.externalContractNo || '尚未登记' }}</el-descriptions-item><el-descriptions-item label="业务类型">{{ detail.businessType || '尚未登记' }}</el-descriptions-item><el-descriptions-item label="签约主体">{{ detail.ourPartyName || '尚未登记' }} / {{ detail.counterpartyName || '尚未登记' }}</el-descriptions-item><el-descriptions-item label="合同金额">¥ {{ money(detail.contractAmount) }}</el-descriptions-item><el-descriptions-item label="签订日期 / 履行截止">{{ detail.signedAt || '尚未登记' }} / {{ detail.expireDate || '尚未登记' }}</el-descriptions-item><el-descriptions-item label="业务摘要">{{ detail.description || '尚未填写' }}</el-descriptions-item></el-descriptions>
        <el-divider content-position="left">签署资料版本</el-divider><PagedItems :items="detail.attachments" v-slot="{ rows: pageRows }"><el-table :data="pageRows" empty-text="尚未上传签署资料"><el-table-column prop="fileName" label="文件" min-width="180" /><el-table-column prop="versionNo" label="版本" width="70" /><el-table-column prop="confirmStatus" label="核验" width="90" /><el-table-column prop="uploadedAt" label="上传时间" min-width="150" /><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click="download(row)">下载</el-button></template></el-table-column></el-table></PagedItems>
        <el-divider content-position="left">签署进度历史</el-divider><PagedItems :items="detail.signingHistory" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="time(item.changedAt)"><strong>{{ progressLabels[item.toProgress] || item.toProgress }}</strong><p>{{ item.fromProgress ? `${progressLabels[item.fromProgress] || item.fromProgress} → ` : '' }}{{ item.reason }}<span v-if="item.correction">（管理员纠正）</span></p><p>操作人：{{ item.operatorName }} · {{ item.operatorRole }}</p></el-timeline-item></el-timeline></PagedItems><el-empty v-if="!detail.signingHistory?.length" description="暂无签署进度历史" />
        <el-divider content-position="left">合同操作记录</el-divider><PagedItems :items="detail.operationHistory" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="time(item.occurredAt)"><strong>{{ item.reason || item.actionCode }}</strong><p>操作人：{{ item.operatorId || '系统任务' }}</p></el-timeline-item></el-timeline></PagedItems><el-empty v-if="!detail.operationHistory?.length" description="暂无操作记录" />
      </template>
    </el-drawer>
    <el-dialog v-model="dialogOpen" :title="dialog === 'edit' ? '编辑合同登记' : '上传合同'" width="min(600px, 94%)" destroy-on-close :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="form-error" /><el-form label-position="top" @submit.prevent="save">
      <template v-if="dialog === 'edit'"><el-form-item label="合同名称" required><el-input v-model="form.contractName" maxlength="160" /></el-form-item><el-form-item label="正式合同编号"><el-input v-model="form.externalContractNo" maxlength="64" /></el-form-item><el-form-item label="业务类型"><el-input v-model="form.businessType" maxlength="64" placeholder="软件开发、设备销售、咨询服务等" /></el-form-item><el-form-item label="我方签约主体"><el-input v-model="form.ourPartyName" maxlength="160" /></el-form-item><el-form-item label="对方签约主体"><el-input v-model="form.counterpartyName" maxlength="160" /></el-form-item><el-form-item label="最终合同金额（元）" required><el-input-number v-model="form.contractAmount" :min="0" :precision="2" controls-position="right" /></el-form-item><el-form-item label="签订日期"><el-date-picker v-model="form.signedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="履行截止日期"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="业务摘要"><el-input v-model="form.description" type="textarea" maxlength="1000" show-word-limit /></el-form-item></template>
      <template v-else><el-form-item label="签署合同文件" required><input type="file" accept=".pdf,.doc,.docx,application/pdf" @change="chooseFile" /><p class="scope-note">支持 PDF、Word，单个文件不超过20MB；文件作为版本保存，不覆盖历史资料。</p></el-form-item><el-form-item label="文件说明"><el-input v-model="form.note" maxlength="500" /></el-form-item></template>
    </el-form><template #footer><el-button :disabled="saving" @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="progressDialog" :title="detail?.canCorrectSigningProgress ? '管理员纠正签署进度' : '更新签署进度'" width="min(520px, 94%)" :close-on-click-modal="!saving">
      <el-alert v-if="detail?.canCorrectSigningProgress" type="warning" :closable="false" title="纠正操作会记录操作人、前后状态和原因；双方已签署必须通过文件提交与内部核验完成。" class="form-error" />
      <el-form label-position="top">
        <el-form-item label="当前进度"><el-input :model-value="progressLabels[detail?.signingProgress] || ''" disabled /></el-form-item>
        <el-form-item label="调整为" required><el-select v-model="progressForm.targetProgress" placeholder="选择签署进度"><el-option v-for="item in progressTargets" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
        <el-alert v-if="progressForm.targetProgress === 'WITHDRAWN'" type="info" :closable="false" title="撤回后不可恢复；已有签署文件的登记不能撤回，需保留原档并另建登记。" class="form-error" />
        <el-form-item label="操作原因" required><el-input v-model="progressForm.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="progressDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!progressForm.targetProgress || !progressForm.reason.trim()" @click="saveProgress">确认更新</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar{display:flex;gap:12px;padding:18px;margin-bottom:18px;flex-wrap:wrap}.toolbar .el-input{width:260px}.toolbar .el-select{width:180px}.scope-note{color:var(--crm-text-secondary);font-size:12px;line-height:1.8}.current-progress{margin:10px 0 0}.current-progress strong{color:var(--crm-primary)}.signing-steps{margin:20px 0 14px}.loading{min-height:250px}.detail-head{display:flex;justify-content:space-between;gap:20px;align-items:start;margin-bottom:18px}.detail-head h2{margin:0 0 6px;font-size:20px;overflow-wrap:anywhere}.detail-head p{color:var(--crm-text-secondary);font-size:13px}.actions{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.actions .el-button{margin:0}.el-input-number,.el-date-editor,.el-select{width:100%}.form-error{margin-bottom:16px}.el-timeline{padding:12px 8px 0 16px}.el-timeline p{color:var(--crm-text-secondary);font-size:13px}@media(max-width:600px){.toolbar .el-input,.toolbar .el-select{width:100%}.toolbar{padding:14px}.signing-steps{overflow-x:auto}}
</style>
