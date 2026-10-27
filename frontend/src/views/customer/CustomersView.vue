<script setup>
import PagedItems from '@/components/PagedItems.vue'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElDialog, ElSelect, ElOption, ElDivider,
  ElTabs, ElTabPane, ElDescriptions, ElDescriptionsItem, ElCheckbox, ElTimeline, ElTimelineItem, ElEmpty } from 'element-plus'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import CustomerProfileFields from './CustomerProfileFields.vue'
import ContactFields from './ContactFields.vue'
import CustomerDictionaryDialog from './CustomerDictionaryDialog.vue'
import { useAuthStore } from '@/store/auth'
import { useCustomerDictionaries } from '@/store/customer-dictionaries'
import { customerStatuses, contactRoles, emptyProfile, emptyContact, profileFrom, customerFilterParams } from '@/utils/customer-rules'
import * as api from '@/api/customers'

const route = useRoute(), router = useRouter(), auth = useAuthStore(), dictionaries = useCustomerDictionaries()
const sea = computed(() => route.path === '/public-pool' ? 'PUBLIC' : 'PRIVATE')
const admin = computed(() => auth.user?.roleCode === 'ADMIN')
const privileged = computed(() => ['ADMIN', 'MANAGER'].includes(auth.user?.roleCode))
const rows = ref([]), owners = ref([]), total = ref(0), page = ref(1), size = ref(10), loading = ref(false), failure = ref('')
const initialFilters = () => ({ keyword: '', customerStatus: '', customerLevel: '', sourceCode: '', industryCode: '', region: '', ownerId: null, tags: [], tagMatch: 'ALL' })
const filters = reactive(initialFilters())
const detail = ref(null), detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), activeTab = ref('profile')
const editorOpen = ref(false), editorMode = ref('create'), editingId = ref(null), editingVersion = ref(0), saving = ref(false), formError = ref(''), conflicted = ref(false)
const profileModel = ref(emptyProfile()), initialContact = ref(emptyContact()), selectedOwner = ref(null), profileForm = ref(), initialContactForm = ref()
const reviewRequired = ref(false), reviewConfirmed = ref(false), reviewReason = ref('')
const classificationOpen = ref(false), classification = reactive({ id: null, version: 0, name: '', customerLevel: 'NORMAL', tags: [] })
const contactOpen = ref(false), contactId = ref(null), contactModel = ref(emptyContact()), contactForm = ref(), primaryLocked = ref(false)
const contactCustomer = ref(null), contactCustomerVersion = ref(0), dictionaryOpen = ref(false)
const followOpen = ref(false), followSaving = ref(false)
const transferOpen = ref(false), transferTarget = ref(null), transferReason = ref(''), transferCustomer = ref(null)
const followModel = reactive({ customerVersion: 0, contactId: null, followMethod: 'PHONE', content: '', resultCode: 'NEED_CONFIRMED', substantiveResult: '', nextAction: '', nextActionAt: '', followedAt: '' })
const PUBLIC_OWNER = '__PUBLIC__'
const publicEditor = computed(() => editorMode.value === 'edit' && sea.value === 'PUBLIC')
let generation = 0, detailGeneration = 0

async function load() {
  const current = ++generation
  loading.value = true; failure.value = ''
  try {
    const data = await api.listCustomers({ sea: sea.value, page: page.value, size: size.value, ...customerFilterParams(filters) })
    if (generation === current) { rows.value = data.list; total.value = data.total }
  } catch (error) { if (generation === current) { rows.value = []; total.value = 0; failure.value = error.message } }
  finally { if (generation === current) loading.value = false }
}
async function initialize() {
  try { await Promise.all([dictionaries.load(), api.listCustomerOwners().then((data) => { owners.value = data })]); await load() }
  catch (error) { failure.value = error.message }
}
function search() { if (page.value === 1) void load(); else page.value = 1 }
function reset() { Object.assign(filters, initialFilters()); search() }
async function show(row) {
  const current = ++detailGeneration
  detailOpen.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null; activeTab.value = 'profile'
  try { const result = await api.getCustomer(row.id); if (current === detailGeneration) detail.value = result }
  catch (error) { if (current === detailGeneration) detailError.value = error.message }
  finally { if (current === detailGeneration) detailLoading.value = false }
}
async function refreshDetail() {
  if (!detail.value) return
  detail.value = await api.getCustomer(detail.value.id)
}
function openFollow() {
  const now = new Date(), later = new Date(now.getTime() + 86400000)
  Object.assign(followModel, { customerVersion: detail.value.version, contactId: detail.value.contacts?.[0]?.id || null,
    followMethod: 'PHONE', content: '', resultCode: 'NEED_CONFIRMED', substantiveResult: '', nextAction: '',
    followedAt: new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0,16),
    nextActionAt: new Date(later.getTime() - later.getTimezoneOffset() * 60000).toISOString().slice(0,16) })
  followOpen.value = true
}
async function saveFollow() {
  if (!followModel.contactId || !followModel.content.trim() || !followModel.substantiveResult.trim() || !followModel.nextAction.trim() || !followModel.nextActionAt || !followModel.followedAt) {
    ElMessage.warning('请填写联系人、沟通记录、结果、下一步动作和时间'); return
  }
  followSaving.value = true
  try {
    await api.addFollowRecord(detail.value.id, { ...followModel, followedAt: followModel.followedAt.replace('T', ' '), nextActionAt: followModel.nextActionAt.replace('T', ' ') })
    followOpen.value = false; ElMessage.success(['NO_RESPONSE','NOT_CONNECTED'].includes(followModel.resultCode) ? '跟进已留痕，不刷新有效跟进时间' : '跟进已提交并暂时生效，待主管查验')
    await refreshDetail(); await load()
  } catch (error) { ElMessage.error(error.message) }
  finally { followSaving.value = false }
}
async function reviewFollow(row, reviewStatus) {
  let reviewReason = null
  if (reviewStatus === 'RETURNED') {
    try { const result = await ElMessageBox.prompt('请填写打回原因，记录仍会保留并撤销有效性。', '打回跟进记录', { inputPattern: /\S+/, inputErrorMessage: '请填写原因' }); reviewReason = result.value }
    catch { return }
  }
  try { await api.reviewFollowRecord(detail.value.id, row.id, { version: detail.value.version, reviewStatus, reviewReason }); ElMessage.success(reviewStatus === 'CONFIRMED' ? '已确认跟进记录' : '已打回并重算跟进时间'); await refreshDetail(); await load() }
  catch (error) { ElMessage.error(error.message) }
}
async function claim(row) {
  try {
    await ElMessageBox.confirm(`领取“${row.customerName}”后，该客户将进入你的私海并启动首联计时。`, '领取公海客户', { type: 'warning' })
    await api.claimCustomer(row.id,row.version); ElMessage.success('领取成功'); await load()
  } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
async function openTransfer(row) {
  try {
    const data = await api.getCustomer(row.id)
    transferCustomer.value = data; transferTarget.value = null; transferReason.value = ''; transferOpen.value = true
  } catch (error) { ElMessage.error(error.message) }
}
async function saveTransfer() {
  if (!transferTarget.value || !transferReason.value.trim()) { ElMessage.warning('请选择接手专员并填写交接原因'); return }
  try {
    await ElMessageBox.confirm('交接立即生效，原负责人保留 7 天只读权限，不能编辑、导出、签约或回款。', '确认客户转交', { type: 'warning' })
    await api.transferCustomer(transferCustomer.value.id,{ version: transferCustomer.value.version,newOwnerId: transferTarget.value,reason: transferReason.value.trim() })
    transferOpen.value = false; ElMessage.success('客户已转交'); await load()
  } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
function clearFeedback() { formError.value = ''; conflicted.value = false; reviewRequired.value = false; reviewConfirmed.value = false; reviewReason.value = '' }
function create() {
  if (!admin.value) return
  clearFeedback(); editorMode.value = 'create'; editingId.value = null; profileModel.value = emptyProfile()
  initialContact.value = { ...emptyContact(), primary: true }
  selectedOwner.value = sea.value === 'PUBLIC' ? PUBLIC_OWNER : null
  editorOpen.value = true
}
async function edit(row) {
  try {
    const data = await api.getCustomer(row.id)
    clearFeedback(); editorMode.value = 'edit'; editingId.value = data.id; editingVersion.value = data.version
    profileModel.value = profileFrom(data); editorOpen.value = true
  } catch (error) { ElMessage.error(error.message) }
}
async function reloadEdit() {
  try { const data = await api.getCustomer(editingId.value); profileModel.value = profileFrom(data); editingVersion.value = data.version; clearFeedback() }
  catch (error) { formError.value = error.message }
}
async function saveProfile() {
  if (!await profileForm.value.validate().catch(() => false)) return
  if (editorMode.value === 'create' && !await initialContactForm.value.validate().catch(() => false)) return
  if (reviewRequired.value && privileged.value && (!reviewConfirmed.value || !reviewReason.value.trim())) {
    formError.value = '请先核对同名情况，勾选确认并填写原因'; return
  }
  if (editorMode.value === 'create' && selectedOwner.value == null) { formError.value = '请选择来源负责人（经理或专员），或明确选择公司公共资源'; return }
  saving.value = true; formError.value = ''; conflicted.value = false
  try {
    const review = { sameNameConfirmed: reviewConfirmed.value, reviewReason: reviewReason.value }
    if (editorMode.value === 'create') {
      const ownerId = selectedOwner.value === PUBLIC_OWNER ? null : selectedOwner.value
      await api.createCustomer({ profile: profileModel.value, primaryContact: initialContact.value, ownerId, ...review })
      editorOpen.value = false; ElMessage.success(ownerId == null ? '客户已创建到公海' : '客户及主要联系人已创建')
      const target = ownerId == null ? '/public-pool' : '/customers'
      if (route.path !== target) await router.push(target); else await load()
    } else {
      await api.editCustomer(editingId.value, { version: editingVersion.value, profile: profileModel.value, ...review })
      editorOpen.value = false; ElMessage.success('客户档案已保存'); await load(); if (detailOpen.value) await refreshDetail()
    }
  } catch (error) { formError.value = error.message; reviewRequired.value = error.code === 40903 || reviewRequired.value; conflicted.value = error.code === 40900 }
  finally { saving.value = false }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.customerName}”？仅允许删除没有关联业务或归属流转的误录客户。记录将保留在数据库中。`, '删除客户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await api.deleteCustomer(row.id, row.version); ElMessage.success('客户已逻辑删除')
    if (detail.value?.id === row.id) { detailOpen.value = false; detail.value = null }
    if (rows.value.length === 1 && page.value > 1) page.value--; else await load()
  } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
async function classify(row) {
  try {
    const data = await api.getCustomer(row.id)
    clearFeedback(); Object.assign(classification, { id: data.id, version: data.version, name: data.customerName, customerLevel: data.customerLevel, tags: [...data.tags] }); classificationOpen.value = true
  } catch (error) { ElMessage.error(error.message) }
}
async function saveClassification() {
  saving.value = true; formError.value = ''
  try {
    await api.classifyCustomer(classification.id, { version: classification.version, customerLevel: classification.customerLevel, tags: classification.tags })
    classificationOpen.value = false; ElMessage.success('分类标签已保存'); await load(); if (detailOpen.value) await refreshDetail()
  } catch (error) { formError.value = error.message }
  finally { saving.value = false }
}
function editContact(row) {
  clearFeedback(); contactId.value = row?.id || null; contactModel.value = row ? { ...emptyContact(), ...row } : emptyContact()
  primaryLocked.value = Boolean(row?.primary); contactCustomer.value = detail.value.id; contactCustomerVersion.value = detail.value.version; contactOpen.value = true
}
async function saveContact() {
  if (!await contactForm.value.validate().catch(() => false)) return
  if (reviewRequired.value && privileged.value && (!reviewConfirmed.value || !reviewReason.value.trim())) {
    formError.value = '请先核对同名情况，勾选确认并填写原因'; return
  }
  saving.value = true; formError.value = ''
  try {
    const body = {
      customerVersion: contactCustomerVersion.value,
      contact: contactModel.value,
      sameNameConfirmed: reviewConfirmed.value,
      reviewReason: reviewReason.value,
    }
    if (contactId.value) await api.editContact(contactCustomer.value, contactId.value, body); else await api.addContact(contactCustomer.value, body)
    contactOpen.value = false; ElMessage.success('联系人已保存'); await refreshDetail(); await load()
  } catch (error) {
    formError.value = error.message
    reviewRequired.value = error.code === 40903 || reviewRequired.value
  }
  finally { saving.value = false }
}
async function removeContact(row) {
  try {
    await ElMessageBox.confirm(`确认删除联系人“${row.contactName}”？已有跟进或提醒关联的联系人不能删除。`, '删除联系人', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await api.deleteContact(detail.value.id, row.id, detail.value.version); ElMessage.success('联系人已删除'); await refreshDetail(); await load()
  } catch (error) { if (error instanceof Error) ElMessage.error(error.message) }
}
const displayTime = (value) => value ? value.replace('T', ' ').substring(0, 19) : '暂无'
watch(sea, () => {
  ++detailGeneration; detailOpen.value = false; detail.value = null; editorOpen.value = false; contactOpen.value = false; classificationOpen.value = false
  rows.value = []; total.value = 0; Object.assign(filters, initialFilters())
  if (page.value === 1) void load(); else page.value = 1
})
watch([page, size], load)
onBeforeUnmount(() => { ++generation; ++detailGeneration })
void initialize()
</script>
<template>
  <div class="customers-page">
    <PageHeading :title="sea === 'PUBLIC' ? '客户公海' : '客户管理'" :description="sea === 'PUBLIC' ? '查看公司公共资源，联系方式由后端脱敏。' : '维护客户档案、联系人及分类标签；专员看本人，经理看团队，管理员看全量。'">
      <el-button v-if="admin" @click="dictionaryOpen = true">分类选项</el-button><el-button :loading="loading" @click="initialize">刷新</el-button><el-button v-if="admin" type="primary" @click="create">{{ sea === 'PUBLIC' ? '录入公海客户' : '新增客户' }}</el-button>
    </PageHeading>
    <el-form :model="filters" label-position="top" class="panel filters" @submit.prevent="search">
      <el-form-item label="客户名称"><el-input v-model="filters.keyword" clearable maxlength="160" placeholder="输入名称搜索" @keyup.enter="search" /></el-form-item>
      <el-form-item label="客户等级"><el-select v-model="filters.customerLevel" clearable placeholder="全部等级"><el-option v-for="item in dictionaries.options('level')" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
      <el-form-item label="客户来源"><el-select v-model="filters.sourceCode" clearable placeholder="全部来源"><el-option v-for="item in dictionaries.options('source')" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
      <el-form-item label="所属行业"><el-select v-model="filters.industryCode" clearable placeholder="全部行业"><el-option v-for="item in dictionaries.options('industry')" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
      <el-form-item label="客户状态"><el-select v-model="filters.customerStatus" clearable placeholder="全部状态"><el-option v-for="(label, code) in customerStatuses" :key="code" :value="code" :label="label" /></el-select></el-form-item>
      <el-form-item label="所在地区"><el-input v-model="filters.region" clearable maxlength="100" placeholder="地区关键词" /></el-form-item>
      <el-form-item v-if="sea === 'PRIVATE' && privileged" label="客户负责人"><el-select v-model="filters.ownerId" clearable filterable placeholder="全部可见负责人"><el-option v-for="item in owners" :key="item.id" :value="item.id" :label="`${item.realName} · ${item.teamName}`" /></el-select></el-form-item>
      <el-form-item label="客户标签" class="tag-filter"><el-select v-model="filters.tags" multiple filterable collapse-tags collapse-tags-tooltip :multiple-limit="10" placeholder="选择标签"><el-option v-for="item in dictionaries.options('tag')" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item>
      <el-form-item label="标签匹配"><el-select v-model="filters.tagMatch"><el-option value="ALL" label="同时包含所选标签" /><el-option value="ANY" label="包含任一所选标签" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="reset">重置</el-button></div>
    </el-form>
    <DataTable :rows="rows" :total="total" :loading="loading" :error="failure" v-model:page="page" @retry="initialize">
      <el-table-column prop="customerName" label="客户名称" min-width="230" show-overflow-tooltip />
      <el-table-column label="所属负责人 / 团队" min-width="180"><template #default="{ row }"><div>{{ row.ownerName || '未分配专员' }}</div><div class="muted">{{ row.teamName || '公司公海' }}</div></template></el-table-column>
      <el-table-column prop="primaryContactName" label="主要联系人" min-width="120" /><el-table-column prop="primaryPhone" label="联系电话" min-width="145" />
      <el-table-column label="客户等级" min-width="110"><template #default="{ row }"><el-tag :type="row.customerLevel === 'KEY' ? 'warning' : 'info'">{{ dictionaries.label('level', row.customerLevel) }}</el-tag></template></el-table-column>
      <el-table-column label="客户标签" min-width="200"><template #default="{ row }"><div class="tag-list"><el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain">{{ dictionaries.label('tag', tag) }}</el-tag><span v-if="!row.tags?.length" class="muted">暂无标签</span></div></template></el-table-column>
      <el-table-column label="来源 / 行业" min-width="150"><template #default="{ row }"><div>{{ dictionaries.label('source', row.sourceCode) }}</div><div class="muted">{{ dictionaries.label('industry', row.industryCode) }}</div></template></el-table-column>
      <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ customerStatuses[row.customerStatus] || row.customerStatus }}</template></el-table-column>
      <el-table-column label="操作" :min-width="admin ? 330 : 260" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="show(row)">详情</el-button><el-button v-if="row.seaType === 'PUBLIC' && auth.user?.roleCode === 'SPECIALIST'" type="primary" link @click="claim(row)">领取</el-button><el-button v-if="admin && row.seaType === 'PRIVATE'" type="primary" link @click="openTransfer(row)">转交</el-button><el-button v-if="row.canEdit" type="primary" link @click="edit(row)">编辑</el-button><el-button v-if="row.canEdit" type="primary" link @click="classify(row)">分类标签</el-button><el-button v-if="row.canDelete" type="danger" link @click="remove(row)">删除</el-button><el-tag v-if="!row.canEdit && row.seaType === 'PRIVATE'" size="small" type="info">交接只读</el-tag></template></el-table-column>
    </DataTable>
    <el-drawer v-model="detailOpen" title="客户档案" size="880px" class="customer-drawer">
      <div v-if="detailLoading" v-loading="true" class="detail-loading" />
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
      <template v-if="detail">
        <div class="detail-title"><div><h2>{{ detail.customerName }}</h2><p>所属负责人：{{ detail.ownerName || '未分配专员' }} · 所属团队：{{ detail.teamName || '公司公海' }}</p></div><el-tag :type="detail.readonly ? 'info' : 'success'">{{ detail.seaType === 'PUBLIC' ? '公海资源' : detail.readonly ? '交接期只读' : '职责范围内' }}</el-tag></div>
        <el-alert v-if="detail.seaType === 'PUBLIC'" title="公海仅展示基础档案和脱敏联系方式，工商信息、备注及联系人明细暂不展示。" type="info" :closable="false" show-icon />
        <el-tabs v-model="activeTab">
          <el-tab-pane label="档案详情" name="profile">
            <div class="detail-actions"><el-button v-if="detail.canEdit" type="primary" @click="edit(detail)">编辑档案</el-button><el-button v-if="detail.canEdit" @click="classify(detail)">调整分类标签</el-button></div>
            <el-descriptions :column="1" border><el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item><el-descriptions-item label="主要联系人">{{ detail.primaryContactName || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ detail.primaryPhone || '未填写' }}</el-descriptions-item><el-descriptions-item label="客户来源">{{ dictionaries.label('source', detail.sourceCode) }}</el-descriptions-item><el-descriptions-item label="所属行业">{{ dictionaries.label('industry', detail.industryCode) }}</el-descriptions-item><el-descriptions-item label="所在地区">{{ detail.region || '未填写' }}</el-descriptions-item><el-descriptions-item label="客户等级">{{ dictionaries.label('level', detail.customerLevel) }}</el-descriptions-item><el-descriptions-item label="客户标签"><div class="tag-list"><el-tag v-for="tag in detail.tags" :key="tag">{{ dictionaries.label('tag', tag) }}</el-tag><span v-if="!detail.tags.length">暂无标签</span></div></el-descriptions-item><el-descriptions-item label="客户状态">{{ customerStatuses[detail.customerStatus] || detail.customerStatus }}</el-descriptions-item>
              <template v-if="detail.seaType === 'PRIVATE'"><el-descriptions-item label="统一社会信用代码">{{ detail.unifiedSocialCreditCode || '未填写' }}</el-descriptions-item><el-descriptions-item label="公司规模">{{ detail.companySize || '未填写' }}</el-descriptions-item><el-descriptions-item label="客户备注"><span class="pre-wrap">{{ detail.remark || '未填写' }}</span></el-descriptions-item><el-descriptions-item label="首次联系截止">{{ displayTime(detail.firstContactDeadlineAt) }}</el-descriptions-item><el-descriptions-item label="最近有效跟进">{{ displayTime(detail.lastEffectiveFollowAt) }}</el-descriptions-item></template>
              <el-descriptions-item label="创建时间（北京时间）">{{ displayTime(detail.createdAt) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
          <el-tab-pane v-if="detail.seaType === 'PRIVATE'" label="联系人" name="contacts">
            <div class="detail-actions"><el-button v-if="detail.canManageContacts" type="primary" @click="editContact()">新增联系人</el-button><p class="muted">主要联系人同时最多一位。更换时自动同步客户主电话。</p></div>
            <PagedItems :items="detail.contacts" v-slot="{ rows: pageRows }"><el-table :data="pageRows" empty-text="暂无联系人"><el-table-column prop="contactName" label="姓名" min-width="110" /><el-table-column prop="phone" label="电话" min-width="145" /><el-table-column prop="jobTitle" label="职务" min-width="100" /><el-table-column prop="email" label="邮箱" min-width="160" /><el-table-column label="角色" min-width="110"><template #default="{ row }">{{ contactRoles[row.decisionRole] || row.decisionRole }}</template></el-table-column><el-table-column label="主要联系人" width="110"><template #default="{ row }">{{ row.primary ? '是' : '否' }}</template></el-table-column><el-table-column v-if="detail.canManageContacts" label="操作" width="130" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="editContact(row)">编辑</el-button><el-button v-if="!row.primary" type="danger" link @click="removeContact(row)">删除</el-button></template></el-table-column></el-table></PagedItems>
          </el-tab-pane>
          <el-tab-pane v-if="detail.seaType === 'PRIVATE'" label="跟进时间线" name="follows">
            <div class="detail-actions"><el-button v-if="detail.canEdit" type="primary" :disabled="!detail.contacts?.length" @click="openFollow">新增跟进</el-button><p class="muted">提交后暂时计入有效时间；待查验期间受回收保护，打回后保留记录并重算。</p></div>
            <PagedItems v-if="detail.followRecords?.length" :items="detail.followRecords" v-slot="{ rows: pageRows }"><el-timeline><el-timeline-item v-for="item in pageRows" :key="item.id" :timestamp="displayTime(item.followedAt)" placement="top">
              <div class="follow-entry"><strong>{{ item.followUserName }} · {{ item.followMethod }}</strong><el-tag size="small" :type="item.reviewStatus === 'RETURNED' ? 'danger' : item.reviewStatus === 'PENDING' ? 'warning' : item.effective ? 'success' : 'info'">{{ { PENDING: '待查验', CONFIRMED: '已确认', RETURNED: '已打回', NOT_REQUIRED: item.effective ? '有效' : '无效' }[item.reviewStatus] || item.reviewStatus }}</el-tag><p>{{ item.content }}</p><p>沟通结果：{{ item.substantiveResult }}；下一步：{{ item.nextAction }}（{{ displayTime(item.nextActionAt) }}）</p><p v-if="item.reviewReason" class="muted">查验意见：{{ item.reviewReason }}</p>
                <div v-if="privileged && item.reviewStatus === 'PENDING' && item.followUserId !== auth.user?.id && (admin || detail.teamId === auth.user?.teamId)" class="detail-actions"><el-button size="small" type="primary" @click="reviewFollow(item, 'CONFIRMED')">确认</el-button><el-button size="small" type="danger" @click="reviewFollow(item, 'RETURNED')">打回</el-button></div>
              </div>
            </el-timeline-item></el-timeline></PagedItems><el-empty v-else description="暂无跟进记录" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
    <el-dialog v-model="transferOpen" title="转交客户" width="520px" destroy-on-close>
      <el-form label-position="top"><el-form-item label="接手专员"><el-select v-model="transferTarget" filterable placeholder="选择同团队启用专员"><el-option v-for="item in owners.filter(user => user.roleCode === 'SPECIALIST' && user.teamId === transferCustomer?.teamId && user.id !== transferCustomer?.ownerId)" :key="item.id" :value="item.id" :label="`${item.realName} · ${item.teamName}`" /></el-select></el-form-item><el-form-item label="交接原因"><el-input v-model="transferReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form>
      <el-alert title="新负责人立即获得完整权限；原负责人保留 7 天只读查询。" type="info" :closable="false" />
      <template #footer><el-button @click="transferOpen = false">取消</el-button><el-button type="primary" @click="saveTransfer">确认转交</el-button></template>
    </el-dialog>
    <el-dialog v-model="followOpen" title="新增跟进记录" width="620px" destroy-on-close :close-on-click-modal="false">
      <el-form label-position="top"><el-form-item label="具体联系人"><el-select v-model="followModel.contactId"><el-option v-for="item in detail?.contacts || []" :key="item.id" :value="item.id" :label="`${item.contactName} · ${item.phone}`" /></el-select></el-form-item><el-form-item label="沟通方式"><el-select v-model="followModel.followMethod"><el-option v-for="item in [{value:'PHONE',label:'电话'},{value:'WECHAT',label:'微信'},{value:'VISIT',label:'拜访'},{value:'EMAIL',label:'邮件'},{value:'OTHER',label:'其他'}]" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item><el-form-item label="跟进时间"><el-input v-model="followModel.followedAt" type="datetime-local" /></el-form-item><el-form-item label="沟通结果"><el-select v-model="followModel.resultCode"><el-option v-for="item in [{value:'NEED_CONFIRMED',label:'需求已确认'},{value:'SOLUTION_DISCUSSED',label:'方案已沟通'},{value:'NEXT_STEP_AGREED',label:'已约定下一步'},{value:'NO_RESPONSE',label:'无回应'},{value:'NOT_CONNECTED',label:'未接通'},{value:'OTHER',label:'其他实质进展'}]" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item><el-form-item label="沟通内容"><el-input v-model="followModel.content" type="textarea" :rows="3" maxlength="1000" /></el-form-item><el-form-item label="实质结果"><el-input v-model="followModel.substantiveResult" maxlength="500" /></el-form-item><el-form-item label="下一步动作"><el-input v-model="followModel.nextAction" maxlength="500" /></el-form-item><el-form-item label="下一步时间"><el-input v-model="followModel.nextActionAt" type="datetime-local" /></el-form-item></el-form>
      <template #footer><el-button :disabled="followSaving" @click="followOpen = false">取消</el-button><el-button type="primary" :loading="followSaving" @click="saveFollow">提交记录</el-button></template>
    </el-dialog>
    <el-dialog v-model="editorOpen" class="customer-dialog" :title="editorMode === 'create' ? '新增客户档案' : '编辑客户档案'" width="760px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="form-error" />
      <CustomerProfileFields ref="profileForm" :model="profileModel" :public-profile="publicEditor" />
      <template v-if="editorMode === 'create'">
        <el-form v-if="admin" label-position="top"><el-form-item label="来源负责人 / 初始归属" required><el-select v-model="selectedOwner" placeholder="选择经理或专员，直接分配至其私海"><el-option v-if="admin" :value="PUBLIC_OWNER" label="公司公共资源（进入公海）" /><el-option v-for="owner in owners" :key="owner.id" :value="owner.id" :label="`${owner.realName} · ${owner.roleCode === 'MANAGER' ? '经理' : '专员'} · ${owner.teamName}`" /></el-select></el-form-item></el-form>
        <el-divider content-position="left">主要联系人</el-divider><ContactFields ref="initialContactForm" :model="initialContact" primary-locked />
      </template>
      <p v-else class="muted">客户主电话通过“联系人”中的主要联系人维护。编辑档案不会刷新有效跟进时间。</p>
      <div v-if="reviewRequired && privileged" class="review-box"><el-checkbox v-model="reviewConfirmed">我已人工核对，同名客户属于不同主体</el-checkbox><el-input v-model="reviewReason" type="textarea" :rows="2" maxlength="500" placeholder="填写区分依据，不填写电话或信用代码等敏感明文" /></div>
      <template #footer><el-button :disabled="saving" @click="editorOpen = false">取消</el-button><el-button v-if="conflicted && editorMode === 'edit'" :disabled="saving" @click="reloadEdit">重新加载（放弃本次输入）</el-button><el-button type="primary" :loading="saving" @click="saveProfile">保存客户</el-button></template>
    </el-dialog>
    <el-dialog v-model="classificationOpen" class="customer-dialog" title="调整分类与标签" width="520px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="form-error" /><p>{{ classification.name }}</p>
      <el-form label-position="top"><el-form-item label="客户等级"><el-select v-model="classification.customerLevel"><el-option v-for="item in dictionaries.options('level', classification.customerLevel)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item><el-form-item label="客户标签（最多10个）"><el-select v-model="classification.tags" multiple filterable :multiple-limit="10"><el-option v-for="item in dictionaries.options('tag', classification.tags)" :key="item.code" :value="item.code" :label="item.label" /></el-select></el-form-item></el-form><p class="muted">本操作只调整分类标签，不改变客户归属、状态或跟进计时。</p>
      <template #footer><el-button :disabled="saving" @click="classificationOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveClassification">保存分类标签</el-button></template>
    </el-dialog>
    <el-dialog v-model="contactOpen" class="customer-dialog" :title="contactId ? '编辑联系人' : '新增联系人'" width="640px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving"><el-alert v-if="formError" type="error" :closable="false" show-icon class="form-error" :title="formError" /><ContactFields ref="contactForm" :model="contactModel" :primary-locked="primaryLocked" /><div v-if="reviewRequired && privileged" class="review-box"><el-checkbox v-model="reviewConfirmed">我已人工核对，同名客户属于不同主体</el-checkbox><el-input v-model="reviewReason" type="textarea" :rows="2" maxlength="500" placeholder="填写区分依据，不填写电话或信用代码等敏感明文" /></div><template #footer><el-button :disabled="saving" @click="contactOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveContact">保存联系人</el-button></template></el-dialog>
    <CustomerDictionaryDialog v-if="admin" v-model="dictionaryOpen" />
  </div>
</template>
<style scoped>
.filters { padding:20px 20px 4px;margin-bottom:20px;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0 16px; }.filter-actions { display:flex;align-items:center;gap:4px;margin-bottom:18px; }.el-select { width:100%; }.tag-list { display:flex;gap:5px;flex-wrap:wrap; }.muted { color:var(--crm-text-secondary);font-size:13px;line-height:1.6; }.detail-title { display:flex;justify-content:space-between;gap:16px;align-items:start;margin-bottom:20px; }.detail-title h2 { font-size:21px;line-height:1.5;margin:0;overflow-wrap:anywhere; }.detail-title p { color:var(--crm-text-secondary); }.detail-actions { display:flex;gap:12px;flex-wrap:wrap;align-items:center;margin:12px 0 18px; }.detail-loading { min-height:200px; }.pre-wrap { white-space:pre-wrap;overflow-wrap:anywhere; }.review-box { background:var(--crm-bg-page);border:1px solid var(--crm-border);padding:16px;border-radius:6px; }.review-box .el-input { margin-top:10px; }
.follow-entry p { margin:8px 0;color:var(--crm-text-secondary);line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere; }
@media(max-width:1100px) { .filters { grid-template-columns:repeat(2,minmax(0,1fr)); } }@media(max-width:600px) { .filters { grid-template-columns:1fr;padding:16px 16px 0; }.detail-title { flex-direction:column; } }
:global(.customer-dialog) { max-width:calc(100vw - 24px); }:global(.customer-drawer) { max-width:100vw; }
</style>
