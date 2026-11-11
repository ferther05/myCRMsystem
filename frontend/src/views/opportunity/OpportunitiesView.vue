<!-- 文件作用：商机列表、阶段门槛、业务证据、协作和合同登记入口。 -->
<script setup>
import ContractWorkspace from '@/components/ContractWorkspace.vue';
import DetailSection from '@/components/DetailSection.vue';
import PagedItems from '@/components/PagedItems.vue';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  ElMessage,
  ElMessageBox,
  ElSelect,
  ElOption,
  ElDialog,
  ElDescriptions,
  ElDescriptionsItem,
  ElDivider,
  ElTabs,
  ElTabPane,
  ElTimeline,
  ElTimelineItem,
  ElEmpty,
  ElInputNumber,
  ElDatePicker,
  ElRadioGroup,
  ElRadio,
} from 'element-plus';
import { Plus, Search, Refresh, Edit, UserFilled, Document, Clock } from '@element-plus/icons-vue';
import PageHeading from '@/components/PageHeading.vue';
import DataTable from '@/components/DataTable.vue';
import { useAuthStore } from '@/store/auth';
import { listCustomers, listCustomerOwners, reviewFollowRecord } from '@/api/customers';
import * as api from '@/api/opportunities';
import * as contractApi from '@/api/contracts';

const auth = useAuthStore();
const admin = computed(/* 判断当前身份是否为管理员。 */ () => auth.user?.roleCode === 'ADMIN');
const router = useRouter();
const route = useRoute();
/** 商机与合同共用一个菜单，查询参数保留刷新及待办定位能力。 */
const workspaceTab = computed({
  get: () => route.query.view === 'contracts' ? 'contracts' : 'opportunities',
  set: (view) => {
    const { contractId, view: previousView, ...query } = route.query;
    void router.replace({ path: '/opportunities', query: { ...query, ...(view === 'contracts' ? { view } : {}) } });
  },
});
/** 合同处理后刷新商机阶段，避免显示已过时的成交/赢单信息。 */
async function refreshContractOpportunity() {
  await load();
  if (detailOpen.value && detail.value) await openDetail(detail.value);
}
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  size = ref(10),
  loading = ref(false),
  failure = ref('');
const filters = reactive({ keyword: '', stageCode: '', activityStatus: '' });
const detail = ref(null),
  detailOpen = ref(false),
  detailLoading = ref(false),
  dialog = ref(''),
  saving = ref(false),
  formError = ref('');
const customers = ref([]),
  members = ref([]),
  customerLoading = ref(false),
  tab = ref('stage');
const followDetail = ref(null);
const narrowDetail = ref(false);
let detailMedia;
/** 窄屏改为顶部分页导航，保留全部页面入口。 */
function updateDetailViewport() { narrowDetail.value = detailMedia.matches; }
onMounted(() => { detailMedia = window.matchMedia('(max-width: 700px)'); updateDetailViewport(); detailMedia.addEventListener('change', updateDetailViewport); });
onUnmounted(() => detailMedia?.removeEventListener('change', updateDetailViewport));
const form = reactive({});
const owners = ref([]),
  customerPage = ref(1),
  customerTotal = ref(0),
  customerKeyword = ref('');
let customerRequest = 0;
/** 拼接负责人姓名、角色和团队名称。 */
const ownerLabel = (owner) =>
  `${owner.realName} · ${owner.roleCode === 'MANAGER' ? '经理' : '专员'} · ${owner.teamName}`;
const labels = {
  NEW: '线索',
  FOLLOWING: '跟进',
  QUOTED: '报价',
  DEAL: '成交',
  WON: '赢单',
  LOST: '输单',
  CLOSED: '关闭',
};
const activityLabels = {
  ACTIVE: '活跃',
  STAGNANT: '停滞',
  CLOSE_PENDING: '待关闭',
  TERMINATED: '已结束',
};
const resultLabels = {
  NEED_CONFIRMED: '需求已确认',
  SOLUTION_DISCUSSED: '方案已沟通',
  NEXT_STEP_AGREED: '约定下一步',
  NO_RESPONSE: '无回应',
  NOT_CONNECTED: '未接通',
  OTHER: '其他实质结果',
};
const reviewLabels = {
  PENDING: '待查验',
  CONFIRMED: '已确认',
  RETURNED: '已打回',
  NOT_REQUIRED: '仅留痕',
};
const active = computed(
  /* 判断当前商机是否仍处于可推进的活跃阶段。 */ () =>
    ['NEW', 'FOLLOWING', 'QUOTED', 'DEAL'].includes(detail.value?.stageCode),
);
const mainStages = computed(
  /* 筛选商机正常推进主链。 */ () =>
    detail.value?.stageProgress?.filter(
      /* 筛选商机正常推进主链。 */ (s) => !['LOST', 'CLOSED'].includes(s.stageCode),
    ) || [],
);
const terminalStages = computed(
  /* 筛选输单和关闭终态。 */ () =>
    detail.value?.stageProgress?.filter(
      /* 筛选输单和关闭终态。 */ (s) => ['LOST', 'CLOSED'].includes(s.stageCode),
    ) || [],
);
const currentProgress = computed(
  /* 取得当前阶段的门槛满足情况。 */ () =>
    detail.value?.stageProgress?.find(/* 取得当前阶段的门槛满足情况。 */ (s) => s.current),
);
const targets = computed(
  /* 仅为有权修正阶段的管理员计算相邻及终态选项。 */ () => {
    const order = ['NEW', 'FOLLOWING', 'QUOTED', 'DEAL', 'WON'];
    const index = order.indexOf(detail.value?.stageCode);
    if (!active.value) return [];
    return detail.value?.canChangeStage
      ? [order[index - 1], order[index + 1], 'LOST', 'CLOSED'].filter(Boolean)
      : [];
  },
);
const dialogTitle = computed(
  /* 按商机操作模式生成弹窗标题。 */ () =>
    ({
      create: '新建商机',
      edit: '编辑商机',
      stage: '管理员修正阶段',
      threshold: '当前商机阈值',
      collaborator: '添加协作人',
      QUOTE: '录入报价',
      CONFIRMATION: '客户合作确认',
      follow: '新增商机跟进',
      decision: '待关闭裁决',
    })[dialog.value],
);
/** 将金额显示为两位小数及千分位。 */
const money = (value) =>
  Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
/** 将日期时间显示为可读文字或缺省提示。 */
const time = (value) => (value ? String(value).replace('T', ' ').substring(0, 19) : '暂无');

/** 加载当前页面所需的数据并维护加载和错误状态。 */
async function load() {
  loading.value = true;
  failure.value = '';
  try {
    const result = await api.listOpportunities({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      stageCode: filters.stageCode || undefined,
      activityStatus: filters.activityStatus || undefined,
    });
    rows.value = result.list;
    total.value = result.total;
  } catch (error) {
    failure.value = error.message;
  } finally {
    loading.value = false;
  }
}
/** 回到第一页并按当前条件重新查询。 */
function search() {
  if (page.value !== 1) page.value = 1;
  else load();
}
watch([page, size], load);
/** 加载所选记录详情并打开详情窗口。 */
async function openDetail(row) {
  if (String(detail.value?.id) !== String(row.id) || !detailOpen.value) { tab.value = 'stage'; followDetail.value = null; }
  detailLoading.value = true;
  detailOpen.value = true;
  try {
    detail.value = await api.getOpportunity(row.id);
  } catch (error) {
    ElMessage.error(error.message);
    detailOpen.value = false;
  } finally {
    detailLoading.value = false;
  }
}
/** 按负责人和关键词分页查询候选客户并忽略过期响应。 */
async function loadCustomers(keyword = customerKeyword.value, targetPage = 1) {
  const request = ++customerRequest;
  customerKeyword.value = keyword;
  customerPage.value = targetPage;
  form.customerId = null;
  if (!form.ownerId) {
    customers.value = [];
    customerTotal.value = 0;
    customerLoading.value = false;
    return;
  }
  customerLoading.value = true;
  try {
    const result = await listCustomers({
      page: targetPage,
      size: 10,
      sea: 'PRIVATE',
      opportunityEligible: true,
      ownerId: form.ownerId,
      keyword: keyword || undefined,
    });
    if (request === customerRequest) {
      customers.value = result.list;
      customerTotal.value = result.total;
    }
  } catch (error) {
    if (request === customerRequest) {
      customers.value = [];
      customerTotal.value = 0;
      formError.value = error.message;
    }
  } finally {
    if (request === customerRequest) customerLoading.value = false;
  }
}
/** 根据操作类型初始化商机弹窗及所需选项。 */
async function openDialog(mode) {
  if (mode === 'create' && !admin.value) return;
  dialog.value = mode;
  formError.value = '';
  Object.keys(form).forEach(/* 逐项清理表单中原有的字段。 */ (key) => delete form[key]);
  Object.assign(form, { version: detail.value?.version });
  if (mode === 'create') {
    Object.assign(form, {
      ownerId: null,
      customerId: null,
      opportunityName: '',
      expectedAmount: 0,
      expectedCloseDate: '',
    });
    await loadCustomers('');
    try {
      owners.value = await listCustomerOwners();
    } catch (error) {
      formError.value = error.message;
    }
  }
  if (mode === 'edit')
    Object.assign(form, {
      opportunityName: detail.value.opportunityName,
      expectedAmount: Number(detail.value.expectedAmount),
      expectedCloseDate: detail.value.expectedCloseDate,
    });
  if (mode === 'stage') Object.assign(form, { targetStage: '', reason: '' });
  if (mode === 'threshold')
    Object.assign(form, {
      stagnationDays: detail.value.stagnationDays,
      graceDays: detail.value.graceDays,
    });
  if (mode === 'collaborator') {
    Object.assign(form, { userId: null, splitRatio: 10 });
    try {
      const all = await api.listOpportunityMembers(detail.value.id);
      members.value = all.filter(
        /* 按当前业务条件筛选记录。 */ (u) =>
          u.id !== detail.value.ownerId &&
          !detail.value.collaborators.some(
            /* 判断集合中是否存在满足当前条件的条目。 */ (c) => c.userId === u.id,
          ),
      );
    } catch (error) {
      formError.value = error.message;
    }
  }
  if (['QUOTE', 'CONFIRMATION'].includes(mode))
    Object.assign(form, {
      kind: mode,
      quoteAmount: Number(detail.value.quoteAmount ?? detail.value.expectedAmount),
      description: '',
    });
  if (mode === 'decision') Object.assign(form, { close: false, reason: '' });
  if (mode === 'follow')
    Object.assign(form, {
      customerVersion: detail.value.customerVersion,
      contactId: detail.value.contacts[0]?.id,
      followMethod: 'PHONE',
      resultCode: 'NEED_CONFIRMED',
      content: '',
      substantiveResult: '',
      nextAction: '',
      // 与客户跟进一致，默认明天同一时间，负责人仍可按实际安排修改。
      nextActionAt: localDateTime(new Date(Date.now() + 86400000)),
      followedAt: localDateTime(new Date()),
    });
}
/** 把本地日期转换为接口所需的无时区时间字符串。 */
function localDateTime(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
/** 检查当前商机操作的必填字段及金额范围。 */
function validation() {
  if (['create', 'edit'].includes(dialog.value)) {
    if (
      (dialog.value === 'create' && (!form.ownerId || !form.customerId)) ||
      !form.opportunityName?.trim() ||
      !form.expectedCloseDate ||
      form.expectedAmount == null ||
      form.expectedAmount < 0
    )
      return '请完整填写负责人、客户、名称、金额和预计成交日期';
  }
  if (dialog.value === 'stage') {
    if (!form.targetStage) return '请选择目标阶段';
    if (!form.reason?.trim()) return '管理员修正阶段必须填写原因，并仍须满足业务门槛';
  }
  if (dialog.value === 'collaborator' && !form.userId) return '请选择协作人';
  if (['QUOTE', 'CONFIRMATION'].includes(dialog.value) && !form.description?.trim())
    return '请填写记录说明';
  if (
    dialog.value === 'QUOTE' &&
    (form.quoteAmount == null || !Number.isFinite(form.quoteAmount) || form.quoteAmount < 0)
  )
    return '请填写有效的报价金额（不得小于 0）';
  if (dialog.value === 'decision' && !form.reason?.trim()) return '请填写裁决原因';
  if (
    dialog.value === 'follow' &&
    ['contactId', 'content', 'substantiveResult', 'nextAction', 'nextActionAt', 'followedAt'].some(
      /* 判断集合中是否存在满足当前条件的条目。 */ (k) => !form[k],
    )
  )
    return '请完整填写跟进信息';
}
/** 按弹窗类型保存商机操作，刷新详情并提示自动推进结果。 */
async function save() {
  formError.value = validation() || '';
  if (formError.value) return;
  saving.value = true;
  const mode = dialog.value,
    id = detail.value?.id,
    previousStage = detail.value?.stageCode;
  try {
    const payload = { ...form };
    if (mode === 'create') await api.createOpportunity(payload);
    else if (mode === 'edit') await api.editOpportunity(id, payload);
    else if (mode === 'stage') await api.changeOpportunityStage(id, payload);
    else if (mode === 'threshold') await api.updateOpportunityThreshold(id, payload);
    else if (mode === 'collaborator') await api.addOpportunityCollaborator(id, payload);
    else if (mode === 'follow') await api.addOpportunityFollow(id, payload);
    else if (mode === 'decision') await api.decideOpportunityClose(id, payload);
    else await api.recordOpportunityEvidence(id, payload);
    dialog.value = '';
    await load();
    if (mode !== 'create') await openDetail({ id });
    if (
      ['follow', 'QUOTE', 'CONFIRMATION'].includes(mode) &&
      detailOpen.value &&
      detail.value?.stageCode !== previousStage
    ) {
      ElMessage.success(
        `保存成功，已从「${labels[previousStage]}」自动推进至「${labels[detail.value.stageCode]}」`,
      );
    } else if (mode === 'QUOTE') {
      ElMessage.success('报价已保存，请查看当前阶段及必需要求');
    } else ElMessage.success('保存成功');
  } catch (error) {
    formError.value = error.message;
  } finally {
    saving.value = false;
  }
}
/** 确认后撤销当前商机协作人。 */
async function removeCollaborator(row) {
  try {
    await ElMessageBox.confirm(`确定移除协作人“${row.realName}”吗？`, '移除协作人', {
      type: 'warning',
    });
    await api.removeOpportunityCollaborator(detail.value.id, row.id, detail.value.version);
    ElMessage.success('协作人已移除');
    await openDetail(detail.value);
    await load();
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  }
}
/** 从商机详情切入统一合同工作区。 */
async function openContractWorkspace() {
  detailOpen.value = false;
  await router.push({ path: '/opportunities', query: { view: 'contracts' } });
}
/** 创建登记草稿并在同一商机管理入口继续办理合同。 */
async function createContract() {
  try {
    await ElMessageBox.confirm(
      '将根据当前成交商机创建合同登记草稿，不会生成合同正文。是否继续？',
      '创建合同登记',
      { type: 'info' },
    );
    const id = await contractApi.createContractFromOpportunity(detail.value.id);
    ElMessage.success('合同登记草稿已创建');
    detailOpen.value = false;
    await router.push({ path: '/opportunities', query: { view: 'contracts', contractId: id } });
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  }
}
/** 管理员确认后执行活跃度扫描并展示处理结果。 */
async function scan() {
  try {
    await ElMessageBox.confirm('按当前阈值重新计算商机活跃度并生成提醒，是否执行？', '扫描商机', {
      type: 'warning',
    });
    loading.value = true;
    const result = await api.scanOpportunityActivity();
    if (result.failedIds.length)
      ElMessage.warning(`已处理 ${result.processed} 条，失败 ${result.failedIds.length} 条`);
    else ElMessage.success(`已处理 ${result.processed} 条，状态变化 ${result.changed} 条`);
    await load();
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
/** 仅本团队经理/管理员查验其他人的有效跟进，不能自审。 */
function canReviewFollow(item) {
  return (
    item.reviewStatus === 'PENDING' &&
    item.followUserId !== auth.user?.id &&
    (admin.value ||
      (auth.user?.roleCode === 'MANAGER' && auth.user?.teamId === detail.value?.teamId))
  );
}
/** 审核后重新查询实际阶段及条件，避免页面只提示成功但保留旧进度。 */
async function reviewFollow(item, pass) {
  const result = await ElMessageBox.prompt(
    pass ? '填写核验意见' : '填写打回原因',
    pass ? '核验当前跟进' : '打回当前跟进',
    {
      inputValue: pass ? '已核对本阶段沟通事实及后续安排' : '',
      inputValidator: (value) => (value?.trim() ? true : '请填写意见'),
    },
  ).catch(() => null);
  if (!result) return;
  saving.value = true;
  try {
    await reviewFollowRecord(detail.value.customerId, item.id, {
      version: detail.value.customerVersion,
      reviewStatus: pass ? 'CONFIRMED' : 'RETURNED',
      reviewReason: result.value.trim(),
    });
    followDetail.value = null;
    await openDetail({ id: detail.value.id });
    await load();
    ElMessage.success(`核验已保存，当前阶段：${labels[detail.value.stageCode]}`);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
/** 支持从客户详情跳转指定商机。 */
async function openRouteOpportunity() {
  if (route.query.id && /^\d+$/.test(String(route.query.id)))
    await openDetail({ id: route.query.id });
}
watch(() => route.query.id, openRouteOpportunity);
onMounted(async () => {
  await load();
  await openRouteOpportunity();
});
</script>

<template>
  <div>
    <PageHeading title="商机管理" eyebrow="OPPORTUNITY">
      <el-button
        v-if="workspaceTab === 'opportunities' && auth.user?.roleCode === 'ADMIN'"
        :icon="Clock"
        :disabled="loading"
        @click="scan"
        >活跃度扫描</el-button
      >
      <el-button v-if="workspaceTab === 'opportunities' && admin" type="primary" :icon="Plus" @click="openDialog('create')"
        >新建商机</el-button
      >
    </PageHeading>
    <el-tabs v-model="workspaceTab" aria-label="商机业务分类">
      <el-tab-pane label="商机列表" name="opportunities" />
      <el-tab-pane label="合同与回款" name="contracts" />
    </el-tabs>
    <ContractWorkspace v-if="workspaceTab === 'contracts'" @updated="refreshContractOpportunity" />
    <div v-show="workspaceTab === 'opportunities'">
    <form class="panel toolbar" @submit.prevent="search">
      <el-input
        v-model="filters.keyword"
        aria-label="搜索商机或客户"
        placeholder="商机或客户名称"
        clearable
      />
      <el-select v-model="filters.stageCode" aria-label="商机阶段" placeholder="全部阶段" clearable>
        <el-option v-for="(label, code) in labels" :key="code" :value="code" :label="label" />
      </el-select>
      <el-select
        v-model="filters.activityStatus"
        aria-label="活跃度"
        placeholder="全部活跃度"
        clearable
      >
        <el-option
          v-for="(label, code) in activityLabels"
          :key="code"
          :value="code"
          :label="label"
        />
      </el-select>
      <el-button type="primary" :icon="Search" native-type="submit" :loading="loading"
        >查询</el-button
      >
      <el-button :icon="Refresh" aria-label="刷新列表" title="刷新列表" @click="load" />
    </form>
    <DataTable
      :rows="rows"
      :total="total"
      :loading="loading"
      :error="failure"
      v-model:page="page"
      @retry="load"
    >
      <el-table-column label="商机 / 客户" min-width="220"><template #default="{ row }"><strong>{{ row.opportunityName }}</strong><div class="muted">{{ row.customerName }}</div></template></el-table-column>

      <el-table-column label="所属负责人 / 团队" min-width="170">
        <template #default="{ row }">
          <div>{{ row.ownerName }}</div>
          <div class="scope-note">{{ row.teamName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="阶段" width="90">
        <template #default="{ row }">
          <el-tag size="small">{{ labels[row.stageCode] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="活跃度" width="100">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="
              row.activityStatus === 'ACTIVE'
                ? 'success'
                : row.activityStatus === 'CLOSE_PENDING'
                  ? 'danger'
                  : 'info'
            "
          >
            {{ activityLabels[row.activityStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="预计金额（元）" align="right" min-width="140">
        <template #default="{ row }">{{ money(row.expectedAmount) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </DataTable>
    </div>

    <el-drawer v-model="detailOpen" title="商机详情" size="min(1120px, 100%)" class="opportunity-detail-drawer">
      <div v-if="detailLoading" v-loading="true" class="loading" />
      <template v-else-if="detail">
        <div class="detail-head">
          <div>
            <h2>{{ detail.opportunityName }}</h2>
            <p>
              {{ detail.customerName }} · 所属负责人：{{ detail.ownerName }} · 所属团队：{{
                detail.teamName
              }}
            </p>
          </div>
          <el-tag effect="dark">{{ labels[detail.stageCode] }}</el-tag>
        </div>
        <el-tabs v-model="tab" class="opportunity-pages" :tab-position="narrowDetail ? 'top' : 'left'">
          <el-tab-pane name="stage" label="阶段推进">
            <section class="stage-panel" aria-label="商机阶段与推进要求">
              <div class="stage-title"><strong>当前阶段：{{ labels[detail.stageCode] }}</strong><span>满足条件后自动推进</span></div>
          <ol class="stage-track">
            <li
              v-for="stage in mainStages"
              :key="stage.stageCode"
              :class="{ current: stage.current, passed: stage.status === 'PASSED' }"
              :aria-current="stage.current ? 'step' : undefined"
            >
              <span class="stage-number">{{ mainStages.indexOf(stage) + 1 }}</span>
              <strong>{{ stage.stageName }}</strong>
              <span class="stage-badge">
                {{ stage.current ? '当前阶段' : stage.status === 'PASSED' ? '已走过' : '后续阶段' }}
              </span>
            </li>
          </ol>

              <template v-if="currentProgress?.nextStage">
                <h3>下一阶段：{{ currentProgress.nextStageName }}</h3>
                <ul class="stage-requirements compact-requirements">
                  <li v-for="requirement in currentProgress.requirements" :key="requirement.code">
                    <el-tag size="small" :type="requirement.met ? 'success' : 'warning'">{{ requirement.met ? '已完成' : '未完成' }}</el-tag>
                    <div><strong>{{ requirement.label }}</strong><el-button v-if="requirement.code === 'CUSTOMER_CONFIRMATION' && !requirement.met && detail.canEdit && active && ['QUOTED', 'DEAL'].includes(detail.stageCode)" class="requirement-shortcut" link type="primary" @click="openDialog('CONFIRMATION')">登记合作确认</el-button><details v-if="requirement.action"><summary>要求说明</summary><p>{{ requirement.action }}</p></details></div>
                    <el-button v-if="requirement.code === 'QUOTE' && !requirement.met && detail.canEdit && active && detail.stageCode !== 'NEW'" link type="primary" @click="openDialog('QUOTE')">录入报价</el-button>
                  </li>
                </ul>
                <p class="scope-note">推进需要本阶段、本轮经核验通过的有效跟进。</p>
              </template>
              <el-empty v-else :image-size="48" description="当前阶段无需继续推进；业务历史仍可查询" />
              <el-alert v-if="currentProgress?.ready" title="条件已满足，下次相关业务保存时系统核对并推进。" type="info" :closable="false" />
            </section>
            <div class="actions"><el-button v-if="active" type="primary" :icon="Plus" @click="openDialog('follow')">新增跟进</el-button><el-button @click="tab = 'evidence'">报价与合作确认</el-button><el-button @click="tab = 'follow'">查看跟进与核验</el-button></div>
            <el-alert v-if="detail.activityStatus === 'CLOSE_PENDING'" title="待关闭裁决中，客户继续受保护；请在规则与管理页处理。" type="warning" :closable="false" />
          </el-tab-pane>

          <el-tab-pane name="overview" label="业务资料">
<DetailSection title="业务概况" emphasis><el-descriptions :column="1" border><el-descriptions-item label="阶段 / 活跃度">
                {{ labels[detail.stageCode] }} / {{ activityLabels[detail.activityStatus] }}
              </el-descriptions-item>
<el-descriptions-item label="预计合同总额"
                >¥ {{ money(detail.expectedAmount) }}
              </el-descriptions-item>
<el-descriptions-item label="预计成交日期">
                {{ detail.expectedCloseDate || '尚未补录' }}
              </el-descriptions-item></el-descriptions></DetailSection>
<div class="actions"><el-button v-if="detail.canEdit && active" :icon="Edit" @click="openDialog('edit')">编辑业务资料</el-button></div>
          </el-tab-pane>
          <el-tab-pane name="evidence" label="报价与确认">
            <div class="evidence-grid">
              <section class="evidence-card"><span class="evidence-step">01 · 报价登记</span><h3>{{ detail.quoteAt ? '已登记报价' : '尚未登记报价' }}</h3><strong class="evidence-value">{{ detail.quoteAt ? '¥ ' + money(detail.quoteAmount) : '待填写金额及说明' }}</strong><p>{{ detail.quoteAt ? time(detail.quoteAt) : '填写正式报价金额与说明，预计金额不代替报价。' }}</p><el-button v-if="detail.canEdit && active && detail.stageCode !== 'NEW'" type="primary" @click="openDialog('QUOTE')">{{ detail.quoteAt ? '更新报价' : '录入报价' }}</el-button><span v-else class="scope-note">{{ detail.stageCode === 'NEW' ? '进入跟进阶段后可登记' : '当前仅可查看' }}</span></section>
              <section class="evidence-card"><span class="evidence-step">02 · 合作确认</span><h3>{{ detail.customerConfirmAt ? '已登记合作确认' : '等待客户明确合作' }}</h3><p>{{ detail.customerConfirmAt ? time(detail.customerConfirmAt) : '客户明确合作后登记确认；系统仍会检查跟进核验等条件。' }}</p><el-button v-if="detail.canEdit && active && ['QUOTED', 'DEAL'].includes(detail.stageCode)" type="primary" plain @click="openDialog('CONFIRMATION')">{{ detail.customerConfirmAt ? '更新合作确认' : '登记合作确认' }}</el-button><span v-else class="scope-note">{{ active ? '报价阶段起可登记' : '当前仅可查看' }}</span></section>
            </div>
            <DetailSection v-if="['DEAL','WON'].includes(detail.stageCode)" title="合同与回款"><p class="scope-note">合同登记、文件核验与回款均在商机管理内办理。</p><div class="actions"><el-button v-if="detail.canEdit && detail.stageCode === 'DEAL'" @click="createContract">创建合同登记</el-button><el-button @click="openContractWorkspace">查看合同与回款</el-button></div></DetailSection>
          </el-tab-pane>
          <el-tab-pane name="collaboration" label="协作成员">
            <div class="actions"><el-button v-if="detail.canManageCollaborators && active" type="primary" :icon="UserFilled" @click="openDialog('collaborator')">添加协作人</el-button></div>
            <DetailSection title="协作成员与分工">
            <PagedItems :items="detail.collaborators" v-slot="{ rows: pageRows }">
              <el-table :data="pageRows" empty-text="暂无协作人">
                <el-table-column prop="realName" label="姓名" />
                <el-table-column label="分成比例">
                  <template #default="{ row }">{{ row.splitRatio }}%</template>
                </el-table-column>
                <el-table-column
                  v-if="detail.canManageCollaborators && active"
                  label="操作"
                  width="80"
                >
                  <template #default="{ row }">
                    <el-button link type="danger" @click="removeCollaborator(row)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </PagedItems></DetailSection>
          </el-tab-pane>
          <el-tab-pane name="rules" label="规则与管理">
<DetailSection title="活跃与停滞规则"><el-descriptions :column="1" border><el-descriptions-item label="停滞阈值">
                {{ detail.stagnationDays }} 天 + 宽限
                {{ detail.graceDays }} 天</el-descriptions-item
              >
<el-descriptions-item label="最近有效推进">
                {{ time(detail.lastEffectiveProgressAt) }}
              </el-descriptions-item></el-descriptions></DetailSection>
<div class="actions" v-if="active"><el-button v-if="detail.canSetThreshold" :icon="Clock" @click="openDialog('threshold')">调整停滞阈值</el-button><el-button v-if="detail.canChangeStage" @click="openDialog('stage')">管理员修正阶段</el-button><el-button v-if="detail.canDecidePending" type="warning" @click="openDialog('decision')">待关闭裁决</el-button></div>
<p class="scope-note">待查验跟进仅暂时刷新活跃计时；打回后撤销有效性并重算，历史保留。</p>
          <details class="secondary-disclosure"><summary>结束分支与管理员修正规则</summary><div class="terminal-stages">
            <span>结束分支：</span>
            <el-tag
              v-for="stage in terminalStages"
              :key="stage.stageCode"
              :type="stage.current ? 'danger' : 'info'"
              :effect="stage.current ? 'dark' : 'plain'"
            >
              {{ stage.stageName }}{{ stage.current ? ' · 当前阶段' : '' }}
            </el-tag>
            <span>仅管理员可修正为输单／关闭，须填写原因并通过合同与回款校验</span>
          </div>
          </details>          <details class="all-stage-rules">
            <summary>查看所有阶段的必需要求及完成情况</summary>
            <article
              v-for="stage in mainStages.filter(
                /* 仅列出存在下一阶段的流转规则。 */ (s) => s.nextStage,
              )"
              :key="stage.stageCode"
            >
              <h4>
                {{ stage.stageName }} → {{ stage.nextStageName
                }}{{ stage.current ? '（当前）' : '' }}
              </h4>
              <ul class="stage-requirements">
                <li v-for="requirement in stage.requirements" :key="requirement.code">
                  <el-tag size="small" :type="requirement.met ? 'success' : 'warning'">
                    {{ requirement.met ? '已完成' : '未完成' }}
                  </el-tag>
                  <span>{{ requirement.label }}</span>
                </li>
              </ul>
            </article>
          </details>
          </el-tab-pane>
          <el-tab-pane name="follow" label="跟进与核验">
            <div class="actions"><el-button v-if="active" type="primary" :icon="Plus" @click="openDialog('follow')">新增跟进</el-button></div>
            <PagedItems :items="detail.followRecords" v-slot="{ rows: pageRows }"><el-table :data="pageRows" empty-text="暂无商机跟进"><el-table-column label="沟通结果" min-width="140"><template #default="{ row }">{{ resultLabels[row.resultCode] || row.resultCode }}</template></el-table-column><el-table-column prop="followUserName" label="跟进人" width="110"/><el-table-column label="核验" width="95"><template #default="{ row }"><el-tag size="small" :type="row.reviewStatus === 'CONFIRMED' ? 'success' : row.reviewStatus === 'RETURNED' ? 'warning' : 'info'">{{ reviewLabels[row.reviewStatus] }}</el-tag></template></el-table-column><el-table-column label="跟进时间" min-width="165"><template #default="{ row }">{{ time(row.followedAt) }}</template></el-table-column><el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="followDetail = row">{{ canReviewFollow(row) ? '详情 / 核验' : '详情' }}</el-button></template></el-table-column></el-table></PagedItems>
          </el-tab-pane>
          <el-tab-pane name="history" label="阶段历史"><PagedItems :items="detail.stageHistory" v-slot="{ rows: pageRows }"><el-table :data="pageRows" empty-text="暂无阶段历史"><el-table-column label="阶段变化" min-width="150"><template #default="{ row }">{{ labels[row.fromStage] || '创建' }} → {{ labels[row.toStage] }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip/><el-table-column prop="operatorName" label="操作人" width="110"/><el-table-column label="时间" min-width="165"><template #default="{ row }">{{ time(row.occurredAt) }}</template></el-table-column></el-table></PagedItems></el-tab-pane>
          <el-tab-pane v-if="detail.canEdit" name="audit" label="操作记录">
            <PagedItems :items="detail.operationHistory" v-slot="{ rows: pageRows }">
              <el-timeline>
                <el-timeline-item
                  v-for="item in pageRows"
                  :key="item.id"
                  :timestamp="time(item.occurredAt)"
                >
                  <strong>{{ item.reason || item.actionCode }}</strong>
                  <p>操作人：{{ item.operatorName || '系统任务' }}</p>
                  <details>
                    <summary>变更明细</summary>
                    <pre>{{ item.beforeJson }}</pre>
                    <pre>{{ item.afterJson }}</pre>
                  </details>
                </el-timeline-item>
              </el-timeline>
            </PagedItems>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog :model-value="Boolean(followDetail)" @close="followDetail = null" title="商机跟进详情" width="min(700px, 94%)"><template v-if="followDetail"><template v-for="item in [followDetail]" :key="item.id">
                  <article class="follow-card">
                    <div class="follow-title">
                      <strong>{{ resultLabels[item.resultCode] || item.resultCode }}</strong>
                      <el-tag
                        size="small"
                        :type="
                          item.reviewStatus === 'CONFIRMED'
                            ? 'success'
                            : item.reviewStatus === 'RETURNED'
                              ? 'warning'
                              : 'info'
                        "
                        >{{ reviewLabels[item.reviewStatus] }}</el-tag
                      >
                      <span
                        >{{ labels[item.opportunityStageCode] || '历史阶段未标注'
                        }}<span v-if="item.opportunityStageSeq != null">
                          · 第{{ item.opportunityStageSeq + 1 }}轮</span
                        ></span
                      >
                    </div>
                    <p class="follow-meta">
                      跟进人：{{ item.followUserName }} · 联系人：{{ item.contactName || '未登记' }}
                    </p>
                    <p><span class="follow-key">沟通内容</span>{{ item.content }}</p>
                    <p><span class="follow-key">实质结果</span>{{ item.substantiveResult }}</p>
                    <p>
                      <span class="follow-key">下一步</span>{{ item.nextAction }} ·
                      {{ time(item.nextActionAt) }}
                    </p>
                    <p v-if="item.reviewerName" class="follow-meta">
                      核验人：{{ item.reviewerName }} · {{ time(item.reviewedAt)
                      }}<span v-if="item.reviewReason"> · {{ item.reviewReason }}</span>
                    </p>
                    <div v-if="canReviewFollow(item)" class="follow-actions">
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        :disabled="saving"
                        @click="reviewFollow(item, true)"
                        >核验通过</el-button
                      >
                      <el-button size="small" :disabled="saving" @click="reviewFollow(item, false)"
                        >打回</el-button
                      >
                    </div>
                  </article>
</template></template></el-dialog>

    <el-dialog
      :model-value="Boolean(dialog)"
      @update:model-value="
        /* 弹窗关闭时清空当前操作标识。 */
        (value) => {
          if (!value) dialog = '';
        }
      "
      :title="dialogTitle"
      width="min(560px, 94%)"
      destroy-on-close
      :close-on-click-modal="!saving"
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        :closable="false"
        show-icon
        class="form-error"
      />
      <el-form label-position="top" @submit.prevent="save">
        <template v-if="['create', 'edit'].includes(dialog)">
          <el-alert
            v-if="dialog === 'edit'"
            title="这里修改的是预计合同总额。正式报价请使用商机详情中的「录入报价」，保存金额和说明后由系统自动判定阶段。"
            type="info"
            :closable="false"
            show-icon
          />
          <template v-if="dialog === 'create'">
            <el-form-item label="私海负责人（经理 / 专员）" required>
              <el-select
                v-model="form.ownerId"
                filterable
                placeholder="选择负责此商机的经理或专员"
                @change="loadCustomers('')"
              >
                <el-option
                  v-for="owner in owners"
                  :key="owner.id"
                  :value="owner.id"
                  :label="ownerLabel(owner)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="该负责人名下的私海客户（跟进中 / 保护中）" required>
              <el-select
                v-model="form.customerId"
                filterable
                remote
                :disabled="!form.ownerId"
                :remote-method="loadCustomers"
                :loading="customerLoading"
                placeholder="搜索客户"
                no-data-text="暂无符合条件的客户，请确认归属及首次有效跟进"
              >
                <el-option
                  v-for="item in customers"
                  :key="item.id"
                  :value="item.id"
                  :label="`${item.customerName} · ${item.ownerName} · ${item.teamName}`"
                />
                <template #footer>
                  <el-pagination
                    :current-page="customerPage"
                    :total="customerTotal"
                    :page-size="10"
                    :pager-count="5"
                    layout="total, prev, next"
                    @update:current-page="
                      /* 按当前关键词加载选定页的客户。 */ (value) =>
                        loadCustomers(customerKeyword, value)
                    "
                  />
                </template>
              </el-select>
            </el-form-item>
            <p class="stage-note">
              新商机归属所选客户的负责人及团队；客户尚未首次有效跟进时，先完成跟进再创建商机。
            </p>
          </template>
          <el-form-item label="商机名称" required>
            <el-input v-model="form.opportunityName" maxlength="160" />
          </el-form-item>
          <el-form-item label="预计合同总额（元）" required>
            <el-input-number
              v-model="form.expectedAmount"
              :min="0"
              :precision="2"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="预计成交日期" required>
            <el-date-picker
              v-model="form.expectedCloseDate"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'stage'">
          <el-alert
            title="日常阶段由系统自动推进。管理员修正仍须相邻流转、满足业务门槛并填写原因。"
            type="info"
            :closable="false"
          />
          <el-form-item label="目标阶段" required>
            <el-select v-model="form.targetStage">
              <el-option
                v-for="stage in targets"
                :key="stage"
                :value="stage"
                :label="labels[stage]"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="修正原因" required>
            <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'threshold'">
          <el-form-item label="停滞天数">
            <el-input-number v-model="form.stagnationDays" :min="1" :max="3650" />
          </el-form-item>
          <el-form-item label="宽限天数">
            <el-input-number v-model="form.graceDays" :min="0" :max="365" />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'collaborator'">
          <el-form-item label="同团队专员" required>
            <el-select v-model="form.userId" filterable>
              <el-option
                v-for="item in members"
                :key="item.id"
                :value="item.id"
                :label="`${item.realName} · ${item.username}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="分成比例（%，合计最多30）">
            <el-input-number v-model="form.splitRatio" :min="0.01" :max="30" :precision="2" />
          </el-form-item>
        </template>
        <template v-else-if="['QUOTE', 'CONFIRMATION'].includes(dialog)">
          <el-alert
            v-if="dialog === 'QUOTE'"
            title="金额默认带入已有报价或预计合同总额，请核对并填写报价说明。报价可以先保存；当前阶段本轮有效跟进经经理或管理员核验通过后，系统才会推进阶段。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-form-item v-if="dialog === 'QUOTE'" label="报价金额（元）" required>
            <el-input-number v-model="form.quoteAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item :label="dialog === 'QUOTE' ? '报价说明' : '记录说明'" required>
            <el-input
              v-model="form.description"
              type="textarea"
              maxlength="500"
              show-word-limit
              :placeholder="
                dialog === 'QUOTE'
                  ? '填写本次报价的产品或服务范围、金额依据、沟通情况等'
                  : '填写客户合作确认的具体情况'
              "
            />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'decision'">
          <el-form-item label="裁决结果">
            <el-radio-group v-model="form.close">
              <el-radio :value="false">继续推进</el-radio>
              <el-radio v-if="detail.canChangeStage" :value="true">确认关闭</el-radio>
            </el-radio-group>
            <p v-if="!detail.canChangeStage" class="stage-note">
              经理可解除待关闭状态；确认关闭由管理员操作。
            </p>
          </el-form-item>
          <el-form-item label="裁决原因" required>
            <el-input v-model="form.reason" type="textarea" maxlength="500" />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'follow'">
          <el-form-item label="联系人" required>
            <el-select v-model="form.contactId">
              <el-option
                v-for="c in detail.contacts"
                :key="c.id"
                :value="c.id"
                :label="`${c.contactName} · ${c.phone}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="沟通方式">
            <el-select v-model="form.followMethod">
              <el-option
                v-for="(label, value) in {
                  PHONE: '电话',
                  WECHAT: '微信',
                  VISIT: '拜访',
                  EMAIL: '邮件',
                  OTHER: '其他',
                }"
                :key="value"
                :value="value"
                :label="label"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="跟进时间" required>
            <el-date-picker
              v-model="form.followedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="沟通原文" required>
            <el-input v-model="form.content" type="textarea" maxlength="1000" />
          </el-form-item>
          <el-form-item label="沟通结果">
            <el-select v-model="form.resultCode">
              <el-option
                v-for="(label, code) in resultLabels"
                :key="code"
                :value="code"
                :label="label"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="具体结果" required>
            <el-input v-model="form.substantiveResult" type="textarea" maxlength="500" />
          </el-form-item>
          <el-form-item label="下一步动作" required>
            <el-input v-model="form.nextAction" maxlength="500" />
          </el-form-item>
          <el-form-item label="下次跟进时间" required>
            <el-date-picker
              v-model="form.nextActionAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialog = ''">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  padding: 18px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.toolbar .el-input {
  width: 240px;
}
.toolbar .el-select {
  width: 150px;
}
.loading {
  min-height: 250px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: start;
  margin-bottom: 18px;
}
.detail-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  overflow-wrap: anywhere;
}
.detail-head p {
  color: var(--crm-text-secondary);
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.actions .el-button {
  margin: 0;
}
.el-select,
.el-date-editor,
.el-input-number {
  width: 100%;
}
.form-error {
  margin-bottom: 16px;
}
.el-timeline {
  padding: 12px 8px 0 16px;
}
.el-timeline p {
  color: var(--crm-text-secondary);
  font-size: 13px;
  overflow-wrap: anywhere;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
}
.el-divider {
  margin: 24px 0 16px;
}
.stage-panel {
  margin: 18px 0;
  padding: 18px;
  border: 1px solid #dce5f1;
  border-radius: 12px;
  background: #f8faff;
}
.stage-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: #123b70;
}
.stage-title span,
.stage-note,
.stage-description {
  color: var(--crm-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.stage-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  padding: 0;
  list-style: none;
  margin: 18px 0;
}
.stage-track li {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 7px;
  padding: 12px 4px;
  border: 1px solid #dce3ed;
  border-radius: 8px;
  background: white;
  color: #68788f;
}
.stage-track li.current {
  border: 2px solid #2563eb;
  background: #eaf2ff;
  color: #123f98;
  box-shadow: 0 0 0 3px #dce9ff;
}
.stage-track li.passed {
  background: #edf7f1;
  color: #28724d;
  border-color: #c6e5d2;
}
.stage-number {
  display: grid;
  place-items: center;
  border-radius: 50%;
  width: 25px;
  height: 25px;
  background: #edf0f5;
}
.current .stage-number {
  background: #2563eb;
  color: white;
}
.stage-badge {
  font-size: 12px;
}
.terminal-stages {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  color: var(--crm-text-secondary);
  font-size: 12px;
}
.stage-panel h3 {
  font-size: 15px;
  margin: 20px 0 12px;
}
.stage-requirements {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 12px;
}
.stage-requirements li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.6;
}
.stage-requirements .el-tag {
  flex-shrink: 0;
}
.stage-requirements p {
  margin: 3px 0 0;
  color: var(--crm-text-secondary);
}
.all-stage-rules summary {
  cursor: pointer;
  color: #245ac2;
  font-size: 13px;
  padding: 8px 0;
}
.all-stage-rules h4 {
  font-size: 13px;
}
@media (max-width: 600px) {
  .stage-panel {
    padding: 12px;
  }
  .stage-track {
    gap: 5px;
  }
  .stage-track li {
    padding: 10px 2px;
  }
  .stage-badge {
    font-size: 10px;
  }
  .stage-track strong {
    font-size: 13px;
  }
}
@media (max-width: 600px) {
  .toolbar .el-input,
  .toolbar .el-select {
    width: 100%;
  }
  .toolbar {
    padding: 14px;
  }
  .detail-head {
    gap: 10px;
  }
}
.follow-card {
  background: #f8fafc;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 14px 16px;
}
.follow-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: #334155;
}
.follow-title > span:last-child,
.follow-meta {
  color: #64748b;
  font-size: 12px;
}
.follow-key {
  display: inline-block;
  width: 78px;
  color: #64748b;
}
.follow-card p {
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.follow-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

/* 详情采用独立短页；导航固定，仅超长内容在当前页内滚动。 */
:global(.opportunity-detail-drawer .el-drawer__header) { margin-bottom: 0; padding: 16px 22px 8px; }
:global(.opportunity-detail-drawer .el-drawer__body) { display: flex; flex-direction: column; overflow: hidden; padding: 12px 22px 20px; }
.opportunity-pages { flex: 1; min-height: 0; display: flex; }
.opportunity-pages :deep(.el-tabs__header) { flex-shrink: 0; }
.opportunity-pages :deep(.el-tabs__item) { justify-content: flex-start; height: 44px; padding: 0 18px; }
.opportunity-pages :deep(.el-tabs__content) { flex: 1; min-width: 0; overflow: auto; padding: 0 4px 12px 18px; }
.opportunity-pages :deep(.el-tab-pane) { min-width: 0; }
.opportunity-pages .stage-panel { margin: 0; padding: 12px; }
.opportunity-pages .stage-panel h3 { margin: 10px 0; }
.opportunity-pages .actions { margin: 10px 0; }
.opportunity-pages .stage-track { margin: 10px 0; }
.opportunity-pages .stage-track li { padding: 6px 4px; gap: 3px; }
.opportunity-pages .stage-number { width: 20px; height: 20px; }
.compact-requirements { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; margin: 10px 0; }
.compact-requirements li { background: #fff; padding: 10px; border: 1px solid #e4e9f0; border-radius: 6px; }
.compact-requirements li > div { flex: 1; min-width: 0; }
.compact-requirements details { font-size: 12px; color: #53647c; margin-top: 4px; }
.compact-requirements summary { cursor: pointer; }
.requirement-shortcut { height: 18px; font-size: 12px; padding: 0; margin-left: 8px; }
.evidence-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.evidence-card { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; border: 1px solid #dce5f1; border-radius: 10px; padding: 22px; background: #fafcff; }
.evidence-card h3, .evidence-card p { margin: 0; }
.evidence-card p { font-size: 13px; color: #53647c; line-height: 1.7; flex: 1; }
.evidence-step { font-size: 12px; color: #53647c; }
.evidence-value { font-size: 20px; color: #243b5a; overflow-wrap: anywhere; }
@media (max-width: 700px) {
  :global(.opportunity-detail-drawer .el-drawer__body) { padding: 10px 12px 14px; }
  .opportunity-pages { flex-direction: column; }
  .opportunity-pages :deep(.el-tabs__content) { padding: 4px 2px 10px; }
  .opportunity-pages :deep(.el-tabs__item) { height: 40px; padding: 0 12px; }
  .evidence-grid, .compact-requirements { grid-template-columns: 1fr; gap: 10px; }
  .evidence-card { padding: 16px; }
  .detail-head h2 { font-size: 17px; }
}
</style>
