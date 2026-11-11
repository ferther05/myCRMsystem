<!-- 文件作用：嵌入商机管理的合同登记、签署核验、附件版本及回款工作区。 -->
<script setup>
import DetailSection from '@/components/DetailSection.vue';
import PagedItems from '@/components/PagedItems.vue';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDivider,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElTabs,
  ElTabPane,
  ElSteps,
  ElStep,
} from 'element-plus';
import DataTable from '@/components/DataTable.vue';
import * as api from '@/api/contracts';
import { registrationErrors, registrationPayload } from '@/utils/contract-registration';

const route = useRoute();
const emit = defineEmits(['updated']);
const detailTab = ref('progress');
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  size = ref(10),
  loading = ref(false),
  failure = ref('');
const filters = reactive({ keyword: '', contractStatus: '', signingProgress: '' });
const listMode = ref('registration'),
  acting = ref(false);
const detail = ref(null),
  detailOpen = ref(false),
  detailLoading = ref(false),
  dialog = ref(''),
  saving = ref(false),
  formError = ref(''),
  selectedFile = ref(null),
  progressDialog = ref(false);
const form = reactive({});
const payments = ref(null);
const paymentLoading = ref(false);
const paymentDialog = ref(false);
const receiptDialog = ref(false);
const paymentSaving = ref(false);
const paymentPlanForm = reactive({
  planId: null,
  version: 0,
  changeDescription: '',
  periods: [{ plannedAmount: 0, dueDate: '', triggerNode: 'OTHER' }],
});
const receiptForm = reactive({
  idempotencyKey: '',
  periodId: null,
  amount: null,
  receivedAt: '',
  paymentMethod: 'BANK_TRANSFER',
  remark: '',
  differenceDescription: '',
});
const editorForm = ref(null),
  fieldErrors = ref({});
const statusLabels = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待经理核验',
  RETURNED: '退回补充',
  SIGNED: '已签约登记',
  PERFORMING: '履行中',
  SETTLED: '已结清',
  TERMINATED: '已终止',
  VOID: '已撤回',
};
const progressLabels = {
  DRAFT_GENERATED: '合同登记草稿',
  DRAFT_REVIEW: '合同登记核对',
  SIGNED_UPLOAD: '合同签署与上传',
  FILE_REVIEW: '合同文件待确认',
  FILE_CONFIRMED: '合同文件已确认',
  EXCEPTION: '进度异常',
  WITHDRAWN: '已撤回',
  // 只用于保留的历史记录，现行流程不再使用旧节点。
  INTERNAL_HANDLING: '内部办理（旧）',
  WAIT_OUR_SIGN: '待我方签署（旧）',
  WAIT_COUNTERPARTY_SIGN: '待对方签署（旧）',
  BOTH_SIGNED: '双方已签署（旧）',
};
const normalSteps = [
  'DRAFT_GENERATED',
  'DRAFT_REVIEW',
  'SIGNED_UPLOAD',
  'FILE_REVIEW',
  'FILE_CONFIRMED',
];
const progressOptions = [...normalSteps, 'EXCEPTION', 'WITHDRAWN'].map((value) => ({
  value,
  label: progressLabels[value],
}));
const progressForm = reactive({ targetProgress: '', reason: '' });
/** 正常步骤由材料动作驱动，仅异常、撤回和有权恢复可供人工选择。 */
const progressTargets = computed(() => {
  if (!detail.value) return [];
  const values = ['EXCEPTION', 'WITHDRAWN'].filter(
    (value) => value !== detail.value.signingProgress,
  );
  if (detail.value.canCorrectSigningProgress) values.unshift('NORMAL');
  return values.map((value) => ({
    value,
    label:
      value === 'NORMAL'
        ? `恢复正常（${progressLabels[detail.value.resumeSigningProgress] || '原步骤'}）`
        : progressLabels[value],
  }));
});
/** 暂停时仍标明保存的步骤，完成节点显示五步全部完成。 */
const signingStep = computed(() => {
  const step = ['EXCEPTION', 'WITHDRAWN'].includes(detail.value?.signingProgress)
    ? detail.value.resumeSigningProgress
    : detail.value?.signingProgress;
  const index = normalSteps.indexOf(step);
  return index === 4 ? 5 : Math.max(0, index);
});
const dialogOpen = computed({
  /* 将弹窗模式转换为是否打开。 */ get: () => dialog.value !== '',
  /* 关闭弹窗时清理操作模式。 */ set: (value) => {
    if (!value) {
      dialog.value = '';
      selectedFile.value = null;
    }
  },
});
/** 标出最新实际签署文件，历史版本仍保留可查。 */
const latestSignedId = computed(
  () =>
    (detail.value?.attachments || [])
      .filter((item) => item.materialType === 'SIGNED_CONTRACT')
      .reduce(
        (latest, item) => (!latest || item.versionNo > latest.versionNo ? item : latest),
        null,
      )?.id,
);
const filterStatuses = computed(
  /* 根据当前列表模式提供合同筛选状态。 */ () =>
    listMode.value === 'registration' ? { UNREGISTERED: '未登记', ...statusLabels } : statusLabels,
);
/** 将金额显示为两位小数及千分位。 */
const money = (value) =>
  Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
/** 将日期时间显示为可读文字或缺省提示。 */
const time = (value) => (value ? String(value).replace('T', ' ').substring(0, 19) : '暂无');
/** 以项目业务时区生成后端接收的无时区日期时间。 */
const businessNow = () =>
  new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).format(new Date()).replace(' ', 'T');
let generation = 0;

/** 加载当前页面所需的数据并维护加载和错误状态。 */
async function load() {
  const current = ++generation;
  const mode = listMode.value;
  loading.value = true;
  failure.value = '';
  try {
    const fetch = mode === 'registration' ? api.listRegistrationOpportunities : api.listContracts;
    const result = await fetch({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      contractStatus: filters.contractStatus || undefined,
      signingProgress: filters.signingProgress || undefined,
    });
    if (current === generation) {
      rows.value = result.list;
      total.value = result.total;
    }
  } catch (error) {
    if (current === generation) {
      rows.value = [];
      total.value = 0;
      failure.value = error.message;
    }
  } finally {
    if (current === generation) loading.value = false;
  }
}
/** 回到第一页并按当前条件重新查询。 */
async function search() {
  page.value = 1;
  await load();
}
watch([page, size], load);
watch(
  listMode,
  /* 读取需要监听的响应式字段。 */ () => {
    filters.contractStatus = '';
    filters.signingProgress = '';
    if (page.value === 1) void load();
    else page.value = 1;
  },
);
/** 加载所选记录详情并打开详情窗口。 */
async function openDetail(rowOrId) {
  detailTab.value = 'progress';
  detail.value = null;
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    detail.value = await api.getContract(typeof rowOrId === 'object' ? rowOrId.id : rowOrId);
    await loadPayments();
  } catch (error) {
    ElMessage.error(error.message);
    detailOpen.value = false;
  } finally {
    detailLoading.value = false;
  }
}
/** 刷新回款详情并显示接口失败状态。 */
async function loadPayments() {
  if (!detail.value?.id) return;
  paymentLoading.value = true;
  try {
    payments.value = await api.getPayments(detail.value.id);
    detail.value = await api.getContract(detail.value.id);
    await load();
  } catch (error) {
    payments.value = null;
    ElMessage.error(`回款信息加载失败：${error.message}`);
  } finally {
    paymentLoading.value = false;
  }
}
/** 准备首版计划或基于已收余额的计划换版草稿。 */
function openPaymentPlan() {
  const received = Number(payments.value?.receivedAmount || 0);
  paymentPlanForm.planId = null;
  paymentPlanForm.version = 0;
  paymentPlanForm.changeDescription = '';
  paymentPlanForm.periods = [
    {
      plannedAmount: Number((Number(detail.value.contractAmount) - received).toFixed(2)),
      dueDate: '',
      triggerNode: 'OTHER',
    },
  ];
  paymentDialog.value = true;
}
/** 将未提交计划回填到计划编辑表单。 */
function editPaymentPlan(plan) {
  paymentPlanForm.planId = plan.id;
  paymentPlanForm.version = plan.version;
  paymentPlanForm.changeDescription = plan.changeDescription || '';
  paymentPlanForm.periods = plan.periods.map((period) => ({
    plannedAmount: Number(period.plannedAmount), dueDate: period.dueDate, triggerNode: period.triggerNode,
  }));
  paymentDialog.value = true;
}
/** 添加一期回款安排。 */
function addPaymentPeriod() {
  if (paymentPlanForm.periods.length >= 24) return ElMessage.warning('每版计划最多24期');
  paymentPlanForm.periods.push({ plannedAmount: 0, dueDate: '', triggerNode: 'OTHER' });
}
/** 提交计划草稿，金额总和由后端按合同余额复核。 */
async function savePaymentPlan() {
  if (paymentSaving.value) return;
  const total = paymentPlanForm.periods.reduce((sum, item) => sum + Number(item.plannedAmount || 0), 0);
  const outstanding = Number(payments.value?.outstandingAmount ?? detail.value.contractAmount);
  if (Math.abs(total - outstanding) > 0.001 || paymentPlanForm.periods.some((item) => !item.dueDate || Number(item.plannedAmount) <= 0)) {
    ElMessage.warning(`各期金额合计需等于未收余额 ¥${money(outstanding)}，并填写每期到期日`);
    return;
  }
  if (payments.value?.plans?.some((item) => item.current) && !paymentPlanForm.changeDescription.trim()) {
    ElMessage.warning('计划换版必须填写差异说明');
    return;
  }
  paymentSaving.value = true;
  try {
    const payload = {
      version: paymentPlanForm.version,
      changeDescription: paymentPlanForm.changeDescription.trim() || null,
      periods: paymentPlanForm.periods.map((item) => ({ ...item, plannedAmount: Number(item.plannedAmount) })),
    };
    if (paymentPlanForm.planId) await api.editPaymentPlan(detail.value.id, paymentPlanForm.planId, payload);
    else await api.createPaymentPlan(detail.value.id, payload);
    paymentDialog.value = false;
    ElMessage.success('回款计划草稿已保存');
    await loadPayments();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    paymentSaving.value = false;
  }
}
/** 提交指定版本的回款计划审批。 */
async function submitPaymentPlan(plan) {
  try {
    await ElMessageBox.confirm(`提交第 ${plan.versionNo} 版回款计划供经理或管理员审批？`, '提交回款计划');
    await api.submitPaymentPlan(detail.value.id, plan.id, { version: plan.version });
    ElMessage.success('回款计划已提交审批');
    await loadPayments();
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  }
}
/** 审批计划版本并要求填写意见。 */
async function reviewPaymentPlan(plan, pass) {
  const result = await ElMessageBox.prompt(pass ? '填写审批意见' : '填写退回原因', pass ? '通过回款计划' : '退回回款计划', {
    inputValue: pass ? '金额与期次安排已核对' : '',
    inputValidator: (value) => value?.trim() ? true : '请填写意见',
  }).catch(() => null);
  if (!result) return;
  try {
    await api.reviewPaymentPlan(detail.value.id, plan.id, { version: plan.version, pass, opinion: result.value.trim() });
    ElMessage.success(pass ? '回款计划已批准' : '回款计划已退回');
    await loadPayments();
  } catch (error) { ElMessage.error(error.message); }
}
/** 打开实际到账登记表单并生成本次请求的幂等标识。 */
async function openReceipt(period) {
  receiptForm.idempotencyKey = crypto.randomUUID();
  receiptForm.periodId = period.id;
  receiptForm.amount = Number(period.outstandingAmount);
  receiptForm.receivedAt = businessNow();
  receiptForm.paymentMethod = 'BANK_TRANSFER';
  receiptForm.remark = '';
  receiptForm.differenceDescription = '';
  receiptDialog.value = true;
}
/** 提交实际到账，部分到账时强制填写差额说明。 */
async function saveReceipt() {
  if (paymentSaving.value) return;
  if (!receiptForm.amount || Number(receiptForm.amount) <= 0) return ElMessage.warning('请输入到账金额');
  const period = payments.value?.plans.flatMap((plan) => plan.periods).find((item) => item.id === receiptForm.periodId);
  if (!period) return ElMessage.error('期次信息已变化，请刷新后重试');
  if (!receiptForm.receivedAt) return ElMessage.warning('请选择实际到账时间');
  if (Number(receiptForm.amount) > Number(period.outstandingAmount)) return ElMessage.warning('到账金额不能超过本期剩余金额');
  if (Number(receiptForm.amount) < Number(period.outstandingAmount) && !receiptForm.differenceDescription.trim()) {
    return ElMessage.warning('部分到账时请填写剩余款项差异说明');
  }
  paymentSaving.value = true;
  try {
    await api.registerPayment(detail.value.id, {
      ...receiptForm,
      amount: Number(receiptForm.amount),
      receivedAt: receiptForm.receivedAt,
      idempotencyKey: receiptForm.idempotencyKey,
    });
    receiptDialog.value = false;
    ElMessage.success('到账记录已登记');
    await loadPayments();
  } catch (error) { ElMessage.error(error.message); }
  finally { paymentSaving.value = false; }
}
/** 管理员作废误录时保留原因和原始记录。 */
async function voidReceipt(receipt) {
  const result = await ElMessageBox.prompt('误录原因将保留在历史记录中。', '作废到账记录', {
    inputType: 'textarea',
    inputValidator: (value) => value?.trim() ? true : '请填写作废原因',
  }).catch(() => null);
  if (!result) return;
  try {
    await api.voidPayment(detail.value.id, receipt.id, { reason: result.value.trim(), version: receipt.version });
    ElMessage.success('到账记录已作废并重新计算余额');
    await loadPayments();
  } catch (error) { ElMessage.error(error.message); }
}
/** 发起合同终止且无应收的审批申请。 */
async function requestTermination() {
  const result = await ElMessageBox.prompt('请填写合同终止原因。', '申请终止且无应收', {
    inputType: 'textarea',
    inputValidator: (value) => value?.trim() ? true : '请填写说明',
  }).catch(() => null);
  if (!result) return;
  const evidence = await ElMessageBox.prompt('请说明为何不存在应收款，供经理或管理员核验。', '无应收依据', {
    inputType: 'textarea',
    inputValidator: (value) => value?.trim() ? true : '请填写无应收依据',
  }).catch(() => null);
  if (!evidence) return;
  try {
    await api.requestTermination(detail.value.id, { reason: result.value.trim(), noReceivableExplanation: evidence.value.trim() });
    ElMessage.success('终止申请已提交审批');
    await loadPayments();
  } catch (error) { ElMessage.error(error.message); }
}
/** 审批终止且无应收申请。 */
async function reviewTermination(pass) {
  const request = payments.value?.termination;
  const result = await ElMessageBox.prompt(pass ? '填写审批意见' : '填写退回原因', '终止申请审批', {
    inputValue: pass ? '已核实无应收' : '',
    inputValidator: (value) => value?.trim() ? true : '请填写审批意见',
  }).catch(() => null);
  if (!result || !request) return;
  try {
    await api.reviewTermination(detail.value.id, { version: request.version, pass, opinion: result.value.trim() });
    ElMessage.success(pass ? '合同已终止' : '终止申请已退回');
    await loadPayments();
    await openDetail(detail.value.id);
  } catch (error) { ElMessage.error(error.message); }
}
/** 为当前成交商机打开合同登记或资料上传入口。 */
async function uploadFromArchive(row) {
  await openDetail(row);
  if (detailOpen.value && detail.value?.canEdit) {
    if (detail.value.signingProgress === 'DRAFT_GENERATED') openEdit();
    else openUpload();
  }
}
/** 把合同登记字段带入编辑弹窗。 */
function openEdit() {
  Object.assign(form, {
    version: detail.value.version,
    contractName: detail.value.contractName,
    externalContractNo: detail.value.externalContractNo || '',
    businessType: detail.value.businessType || '',
    ourPartyName: detail.value.ourPartyName || '',
    counterpartyName: detail.value.counterpartyName || '',
    contractAmount: Number(detail.value.contractAmount),
    signedAt: detail.value.signedAt || '',
    expireDate: detail.value.expireDate || '',
    description: detail.value.description || '',
    ourContactName: detail.value.ourContactName || '',
    ourContactPhone: detail.value.ourContactPhone || '',
    counterpartyContactName: detail.value.counterpartyContactName || '',
    counterpartyContactPhone: detail.value.counterpartyContactPhone || '',
    ourAddress: detail.value.ourAddress || '',
    counterpartyAddress: detail.value.counterpartyAddress || '',
    ourCreditCode: detail.value.ourCreditCode || '',
    counterpartyCreditCode: detail.value.counterpartyCreditCode || '',
    paymentTerms: detail.value.paymentTerms || '',
    deliveryTerms: detail.value.deliveryTerms || '',
    plannedSignedAt: detail.value.plannedSignedAt || '',
    performanceStartDate: detail.value.performanceStartDate || '',
  });
  formError.value = '';
  fieldErrors.value = {};
  dialog.value = 'edit';
}
/** 初始化附件上传表单。 */
function openUpload() {
  selectedFile.value = null;
  form.note = '';
  formError.value = '';
  fieldErrors.value = {};
  dialog.value = 'upload';
}
/** 办理成交商机的合同登记草稿或打开已有登记。 */
async function registrationAction(row, mode) {
  acting.value = true;
  try {
    const id = row.contractId || (await api.createContractFromOpportunity(row.opportunityId));
    const current = await api.getContract(id);
    detail.value = current;
    detailTab.value = 'progress';
    detailOpen.value = true;
    detailLoading.value = false;
    await loadPayments();
    if (current.canEdit) {
      if (mode === 'upload' && current.signingProgress !== 'DRAFT_GENERATED') openUpload();
      else openEdit();
    }
    await load();
  } catch (error) {
    ElMessage.error(error.message);
    await load();
  } finally {
    acting.value = false;
  }
}
/** 记录用户选择的合同附件。 */
function chooseFile(event) {
  selectedFile.value = event.target.files?.[0] || null;
}
/** 在固定按钮区显示失败原因，并滚动到需要修改的第一个字段。 */
async function showFormErrors(errors = {}, message = '') {
  fieldErrors.value = errors;
  const messages = Object.values(errors);
  formError.value = messages.length
    ? `${messages[0]}${messages.length > 1 ? `；另有${messages.length - 1}项，请按字段提示修改` : ''}`
    : message || '操作未完成，请稍后重试';
  await nextTick();
  if (dialog.value === 'edit' && messages.length) {
    editorForm.value?.scrollToField(Object.keys(errors)[0]);
  }
}
/** 按保存或送审要求校验登记信息，失败保留表单及具体原因。 */
async function save(submitRegistration = false) {
  if (saving.value) return;
  formError.value = '';
  fieldErrors.value = {};
  const payload = dialog.value === 'edit' ? registrationPayload(form) : null;
  if (payload) {
    const errors = registrationErrors(payload, submitRegistration === true);
    if (Object.keys(errors).length) {
      await showFormErrors(errors);
      return;
    }
  }
  saving.value = true;
  try {
    if (dialog.value === 'edit') {
      await api.editContract(detail.value.id, payload);
      const current = await api.getContract(detail.value.id);
      detail.value = current;
      form.version = current.version;
      if (submitRegistration === true)
        await api.submitContractDraft(current.id, { version: current.version });
    }
    if (dialog.value === 'upload') {
      if (!selectedFile.value) {
        formError.value = '请选择合同文件';
        saving.value = false;
        return;
      }
      const data = new FormData();
      data.append('materialType', detail.value.uploadMaterialType);
      data.append('note', form.note || '');
      data.append('file', selectedFile.value);
      await api.uploadContractAttachment(detail.value.id, data);
    }
    dialog.value = '';
    selectedFile.value = null;
    ElMessage.success(submitRegistration === true ? '登记表已提交经理或管理员核对' : '操作成功');
    await load();
    await openDetail(detail.value.id);
  } catch (error) {
    await showFormErrors(error.data?.fieldErrors || {}, error.message);
  } finally {
    saving.value = false;
  }
}
/** 提交当前已保存的在线登记信息，不生成草稿文件。 */
async function submitDraft() {
  try {
    await ElMessageBox.confirm(
      '将提交已保存的合同登记表供经理或管理员核对；待审期间锁定信息，负责人可撤回修改。是否继续？',
      '提交登记核对',
      { type: 'info' },
    );
    acting.value = true;
    const id = detail.value.id;
    await api.submitContractDraft(id, { version: detail.value.version });
    ElMessage.success('登记表已提交经理或管理员核对');
    await load();
    await openDetail(id);
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  } finally {
    acting.value = false;
  }
}
/** 负责人主动撤回待审表单，回到登记草稿继续编辑。 */
async function withdrawDraft() {
  try {
    const { value } = await ElMessageBox.prompt(
      '填写撤回原因，撤回后可修改并重新提交。',
      '撤回本次登记提交',
      {
        inputType: 'textarea',
        inputValidator: (text) => (!!text?.trim() && text.length <= 500) || '请填写1至500字原因',
      },
    );
    const id = detail.value.id;
    acting.value = true;
    await api.withdrawContractDraft(id, { version: detail.value.version, reason: value.trim() });
    ElMessage.success('已撤回登记提交，可继续修改');
    await load();
    await openDetail(id);
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  } finally {
    acting.value = false;
  }
}
/** 确认后提交合同资料供内部核验。 */
async function submit() {
  try {
    await ElMessageBox.confirm('提交后将锁定登记字段，等待经理核验。是否继续？', '提交合同核验', {
      type: 'warning',
    });
    await api.submitContract(detail.value.id, { version: detail.value.version });
    ElMessage.success('已提交经理核验');
    await load();
    await openDetail(detail.value.id);
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) ElMessage.error(error.message);
  }
}
/** 填写核验意见后通过或退回合同资料。 */
async function review(pass) {
  const draft = detail.value.signingProgress === 'DRAFT_REVIEW';
  const opinion = await ElMessageBox.prompt(
    pass ? (draft ? '填写登记核对意见' : '填写文件核验意见') : '填写退回原因',
    pass ? (draft ? '通过登记核对' : '通过合同核验') : '退回合同资料',
    {
      inputValue: pass ? (draft ? '已核对登记信息' : '已核对登记信息与最新签署文件') : '',
      /* 要求核验或退回意见非空。 */ inputValidator: (value) =>
        value?.trim() ? true : '请填写意见',
    },
  ).catch(/* 取消输入时返回空值，停止当前操作。 */ () => null);
  if (!opinion) return;
  try {
    await api.reviewContract(detail.value.id, {
      version: detail.value.version,
      pass,
      opinion: opinion.value,
    });
    ElMessage.success(
      pass
        ? draft
          ? '登记核对通过，已进入合同签署与上传'
          : '文件核验通过，系统已检查商机自动推进条件'
        : '已退回补充',
    );
    await load();
    await openDetail(detail.value.id);
  } catch (error) {
    ElMessage.error(error.message);
  }
}
/** 初始化签署进度更新弹窗。 */
function openProgress() {
  progressForm.targetProgress = progressTargets.value[0]?.value || '';
  progressForm.reason = '';
  progressDialog.value = true;
}
/** 校验原因及目标节点后提交签署进度。 */
async function saveProgress() {
  if (!progressForm.targetProgress || !progressForm.reason.trim()) return;
  saving.value = true;
  try {
    await api.updateSigningProgress(detail.value.id, {
      version: detail.value.version,
      targetProgress: progressForm.targetProgress,
      reason: progressForm.reason.trim(),
    });
    progressDialog.value = false;
    ElMessage.success('签署进度已更新');
    await load();
    await openDetail(detail.value.id);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
/** 请求附件接口下载指定版本并显示失败原因。 */
async function download(item) {
  try {
    await api.downloadContractAttachment(detail.value.id, item.id, item.fileName);
  } catch (error) {
    ElMessage.error(error.message || '下载失败');
  }
}
/** 在商机管理内打开待办或旧链接定位的合同；非法编号不发请求。 */
async function openRouteContract() {
  const id = route.query.contractId;
  if (id && /^\d+$/.test(String(id))) await openDetail(Number(id));
}
watch(() => route.query.contractId, openRouteContract);
watch(detailOpen, (open) => { if (!open) emit('updated'); });
onMounted(async () => {
  await load();
  await openRouteContract();
});
</script>

<template>
  <div>
    <el-tabs v-model="listMode">
      <el-tab-pane label="成交商单登记" name="registration" />
      <el-tab-pane label="已登记档案" name="archive" />
    </el-tabs>
    <el-alert
      v-if="listMode === 'registration'"
      title="仅列出当前处于成交阶段的商机。请点击对应商单后的“填写登记草稿”或“上传签署合同”；核验后赢单的合同可在“已登记档案”查询。"
      type="info"
      :closable="false"
      show-icon
      class="form-error"
    />
    <form class="panel toolbar" @submit.prevent="search">
      <el-input
        v-model="filters.keyword"
        :placeholder="
          listMode === 'registration' ? '商单、客户或内部合同编号' : '合同、客户或内部编号'
        "
        clearable
      />
      <el-select v-model="filters.contractStatus" placeholder="全部登记状态" clearable>
        <el-option
          v-for="(label, code) in filterStatuses"
          :key="code"
          :value="code"
          :label="label"
        />
      </el-select>
      <el-select v-model="filters.signingProgress" placeholder="全部签署进度" clearable>
        <el-option
          v-for="item in progressOptions"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-select>
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
      <el-button @click="load">刷新</el-button>
    </form>
    <DataTable
      :rows="rows"
      :total="total"
      :loading="loading"
      :error="failure"
      v-model:page="page"
      @retry="load"
    >
      <el-table-column
        v-if="listMode === 'registration'"
        prop="opportunityName"
        label="成交商单"
        min-width="220"
      />
      <el-table-column v-else prop="contractName" label="合同名称" min-width="200" />
      <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
      <el-table-column label="所属负责人 / 团队" min-width="170">
        <template #default="{ row }">
          <div>{{ row.ownerName }}</div>
          <div class="scope-note">{{ row.teamName }}</div>
        </template>
      </el-table-column>

      <el-table-column label="登记状态" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ statusLabels[row.contractStatus] || '未登记' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签署进度" width="125">
        <template #default="{ row }">
          {{ progressLabels[row.signingProgress] || '未开始' }}
        </template>
      </el-table-column>

      <el-table-column
        :label="listMode === 'registration' ? '预计金额（元）' : '合同金额（元）'"
        align="right"
        min-width="140"
      >
        <template #default="{ row }">
          {{ money(listMode === 'registration' ? row.expectedAmount : row.contractAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" :width="listMode === 'registration' ? 190 : 150" fixed="right">
        <template #default="{ row }">
          <template v-if="listMode === 'registration'">
            <el-button
              v-if="row.canUpload"
              link
              type="primary"
              :disabled="acting"
              @click="registrationAction(row, 'upload')"
              >{{
                !row.signingProgress || row.signingProgress === 'DRAFT_GENERATED'
                  ? '填写登记草稿'
                  : '上传签署合同'
              }}</el-button
            >
            <el-button
              v-if="row.contractId"
              link
              type="primary"
              :disabled="acting"
              @click="openDetail(row.contractId)"
              >详情</el-button
            >
          </template>
          <template v-else>
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.canEdit" link type="primary" @click="uploadFromArchive(row)">{{
              row.signingProgress === 'DRAFT_GENERATED' ? '填写登记草稿' : '上传签署合同'
            }}</el-button>
          </template>
        </template>
      </el-table-column>
    </DataTable>
    <p class="scope-note">
      合同登记支持一条商机一份主合同登记；不同业务领域的具体条款由实际合同文件承载，补充协议以新资料版本归档。
    </p>

    <el-drawer v-model="detailOpen" title="合同详情" size="min(900px, 100%)">
      <div v-if="detailLoading" v-loading="true" class="loading" />
      <template v-else-if="detail">
        <div class="detail-head">
          <div>
            <h2>{{ detail.contractName }}</h2>
            <p>
              {{ detail.customerName }} · {{ detail.opportunityName }} · {{ detail.contractNo }}
            </p>
            <p>所属负责人：{{ detail.ownerName }} · 所属团队：{{ detail.teamName }}</p>
          </div>
          <el-tag>{{ statusLabels[detail.contractStatus] }}</el-tag>
        </div>
        <div class="detail-keyfacts"><div><span>合同金额</span><strong>¥ {{ money(detail.contractAmount) }}</strong></div><div><span>签署节点</span><strong>{{ progressLabels[detail.signingProgress] }}</strong></div><div><span>回款状态</span><strong>{{ payments ? ({ UNPAID:'待回款', PARTIAL:'部分到账', OVERDUE:'存在逾期', RECEIVED:'已收齐待签署', SETTLED:'已结清', TERMINATED:'终止无应收' }[payments.paymentStatus] || payments.paymentStatus) : '加载中' }}</strong></div></div>
<el-tabs v-model="detailTab" class="detail-tabs"><el-tab-pane label="办理进度" name="progress">
<details class="secondary-disclosure"><summary>登记与核验办理说明</summary>        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="合同草稿就是在线登记表，可反复保存修改后提交核对，不需要草稿文件；实际签署合同在登记核对通过后上传。"
        />
        <p v-if="detail.canEdit" class="scope-note">
          草稿可先保存；提交登记核对前需补齐名称、业务类型、双方主体及联系人电话、金额和业务内容。实际签订日期可在签署后补填；最终文件核验通过且商机前序条件齐全后，系统自动推进赢单。
        </p>
</details>
        <el-divider content-position="left">签署进度</el-divider>
        <el-alert
          v-if="detail.signingProgress === 'EXCEPTION'"
          type="warning"
          :closable="false"
          title="当前进度异常；经理或管理员可恢复到暂停前步骤，恢复时重新核对材料。"
        />
        <el-alert
          v-if="detail.signingProgress === 'WITHDRAWN'"
          type="info"
          :closable="false"
          title="当前登记已暂停为撤回；经理或管理员可恢复原步骤，既有文件和确认历史保留。"
        />
        <el-steps :active="signingStep" finish-status="success" align-center class="signing-steps">
          <el-step v-for="step in normalSteps" :key="step" :title="progressLabels[step]" />
        </el-steps>
        <p class="scope-note current-progress">
          当前签署节点：<strong>{{
            progressLabels[detail.signingProgress] || detail.signingProgress
          }}</strong
          >；内部核验状态：<strong>{{
            statusLabels[detail.contractStatus] || detail.contractStatus
          }}</strong>
        </p>
        <el-alert
          :title="detail.progressInstruction"
          :closable="false"
          type="info"
          class="progress-help"
        />
        <div class="actions">
          <el-button v-if="detail.canEdit" @click="openEdit">编辑登记</el-button>
          <el-button
            v-if="detail.canSubmitDraft"
            type="primary"
            :loading="acting"
            @click="submitDraft"
          >
            提交登记核对
          </el-button>
          <el-button v-if="detail.canWithdrawDraft" :loading="acting" @click="withdrawDraft"
            >撤回本次登记提交</el-button
          >
          <el-button v-if="detail.canEdit && detail.uploadMaterialType" @click="openUpload"
            >上传实际签署合同</el-button
          >
          <el-button
            v-if="detail.canUpdateSigningProgress || detail.canCorrectSigningProgress"
            @click="openProgress"
          >
            {{ detail.canCorrectSigningProgress ? '恢复／调整异常状态' : '异常／撤回' }}
          </el-button>
          <el-button v-if="detail.canSubmit" type="primary" @click="submit"
            >确定提交签署文件</el-button
          >
          <el-button v-if="detail.canReview" type="success" @click="review(true)"
            >核验通过</el-button
          >
          <el-button v-if="detail.canReview" type="warning" @click="review(false)"
            >退回补充</el-button
          >
        </div>
</el-tab-pane><el-tab-pane label="登记资料" name="registration"><DetailSection title="合同标识与金额" emphasis><el-descriptions :column="1" border><el-descriptions-item label="内部编号">{{ detail.contractNo }}</el-descriptions-item>
<el-descriptions-item label="正式合同编号">
            {{ detail.externalContractNo || '尚未登记' }}
          </el-descriptions-item>
<el-descriptions-item label="业务类型">
            {{ detail.businessType || '尚未登记' }}
          </el-descriptions-item>
<el-descriptions-item label="合同金额"
            >¥ {{ money(detail.contractAmount) }}
          </el-descriptions-item></el-descriptions></DetailSection>
<DetailSection title="签约双方与联系人"><el-descriptions :column="1" border><el-descriptions-item label="签约主体">
            {{ detail.ourPartyName || '尚未登记' }} / {{ detail.counterpartyName || '尚未登记' }}
          </el-descriptions-item>
<el-descriptions-item label="我方联系人 / 电话"
            >{{ detail.ourContactName || '尚未填写' }} /
            {{ detail.ourContactPhone || '尚未填写' }}</el-descriptions-item
          >
<el-descriptions-item label="对方联系人 / 电话"
            >{{ detail.counterpartyContactName || '尚未填写' }} /
            {{ detail.counterpartyContactPhone || '尚未填写' }}</el-descriptions-item
          >
<el-descriptions-item label="我方地址 / 信用代码"
            >{{ detail.ourAddress || '尚未填写' }} /
            {{ detail.ourCreditCode || '尚未填写' }}</el-descriptions-item
          >
<el-descriptions-item label="对方地址 / 信用代码"
            >{{ detail.counterpartyAddress || '尚未填写' }} /
            {{ detail.counterpartyCreditCode || '尚未填写' }}</el-descriptions-item
          ></el-descriptions></DetailSection>
<DetailSection title="履行日期与业务约定"><el-descriptions :column="1" border><el-descriptions-item label="签订日期 / 履行截止">
            {{ detail.signedAt || '尚未登记' }} / {{ detail.expireDate || '尚未登记' }}
          </el-descriptions-item>
<el-descriptions-item label="预计签订 / 履行开始"
            >{{ detail.plannedSignedAt || '尚未填写' }} /
            {{ detail.performanceStartDate || '尚未填写' }}</el-descriptions-item
          >
<el-descriptions-item label="付款约定">{{
            detail.paymentTerms || '尚未填写'
          }}</el-descriptions-item>
<el-descriptions-item label="交付或验收约定">{{
            detail.deliveryTerms || '尚未填写'
          }}</el-descriptions-item>
<el-descriptions-item label="业务摘要">
            {{ detail.description || '尚未填写' }}
          </el-descriptions-item></el-descriptions></DetailSection></el-tab-pane><el-tab-pane label="回款结清" name="payments">
        <h3 class="detail-subtitle">回款总览</h3>
        <div v-loading="paymentLoading" class="payment-panel">
          <template v-if="payments">
            <div class="payment-summary">
              <div><span>合同金额</span><strong>¥ {{ money(payments.contractAmount) }}</strong></div>
              <div><span>累计到账</span><strong>¥ {{ money(payments.receivedAmount) }}</strong></div>
              <div><span>未收余额</span><strong>¥ {{ money(payments.outstandingAmount) }}</strong></div>
              <div><span>回款状态</span><el-tag :type="payments.paymentStatus === 'OVERDUE' ? 'danger' : payments.paymentStatus === 'SETTLED' ? 'success' : 'info'">{{ { UNPAID: '待回款', PARTIAL: '部分到账', OVERDUE: '存在逾期', RECEIVED: '款项已收齐，待签署确认', SETTLED: '已结清', TERMINATED: '终止无应收' }[payments.paymentStatus] || payments.paymentStatus }}</el-tag></div>
            </div>
            <div class="actions payment-actions">
              <el-button v-if="payments.canManagePlan" type="primary" @click="openPaymentPlan">新建回款计划／换版</el-button>
              <el-button v-if="payments.canRequestTermination" @click="requestTermination">申请终止且无应收</el-button>
            </div>
            <el-alert v-if="payments.termination?.status === 'PENDING'" type="warning" :closable="false" title="存在待审批的终止且无应收申请。" />
            <p v-if="payments.termination" class="scope-note">终止申请：{{ { PENDING: '待审核', APPROVED: '已批准', REJECTED: '已退回' }[payments.termination.status] || payments.termination.status }}；原因：{{ payments.termination.reason }}；无应收依据：{{ payments.termination.noReceivableExplanation }}；审核意见：{{ payments.termination.opinion || '待审核' }}</p>
            <div v-if="payments.canReviewTermination && payments.termination?.status === 'PENDING'" class="actions">
              <el-button type="success" @click="reviewTermination(true)">批准终止</el-button>
              <el-button type="warning" @click="reviewTermination(false)">退回申请</el-button>
            </div>
            <DetailSection title="回款计划与期次" description="先审批计划，再按期次登记到账；历史版本保留。"><el-empty v-if="!payments.plans?.length" description="尚未建立回款计划" />
            <section v-for="plan in payments.plans" :key="plan.id" class="payment-plan">
              <div class="payment-plan-head">
                <strong>第 {{ plan.versionNo }} 版计划</strong>
                <el-tag :type="plan.approvalStatus === 'SUSPENDED' ? 'warning' : plan.current ? 'success' : 'info'">{{ plan.current ? '当前版本' : ['DRAFT', 'PENDING', 'RETURNED'].includes(plan.approvalStatus) ? '候选版本' : '历史版本' }} · {{ { DRAFT: '草稿', PENDING: '待审批', APPROVED: '已批准', ACTIVE: '生效中', RETURNED: '已退回', CHANGED: '已换版', SUSPENDED: '已暂停，需重新编制并审批', TERMINATED: '合同已终止' }[plan.approvalStatus] || plan.approvalStatus }}</el-tag>
                <span>计划总额 ¥ {{ money(plan.totalPlannedAmount) }}</span>
              </div>
              <p v-if="plan.changeDescription" class="scope-note">版本说明：{{ plan.changeDescription }}</p>
              <p v-if="plan.submittedAt" class="scope-note">提交：{{ plan.submitterName }} · {{ time(plan.submittedAt) }}<span v-if="plan.approvedAt">；审核：{{ plan.approverName }} · {{ time(plan.approvedAt) }}</span></p>
              <div class="actions" v-if="plan.approvalStatus === 'DRAFT' || plan.approvalStatus === 'RETURNED'">
                <el-button v-if="payments.canManagePlan" size="small" @click="editPaymentPlan(plan)">编辑草稿</el-button>
                <el-button v-if="payments.canManagePlan" size="small" type="primary" @click="submitPaymentPlan(plan)">提交审批</el-button>
              </div>
              <div class="actions" v-if="plan.approvalStatus === 'PENDING' && payments.canReviewPlan">
                <el-button size="small" type="success" @click="reviewPaymentPlan(plan, true)">批准计划</el-button>
                <el-button size="small" type="warning" @click="reviewPaymentPlan(plan, false)">退回计划</el-button>
              </div>
              <el-table :data="plan.periods" size="small" empty-text="无期次">
                <el-table-column prop="periodNo" label="期次" width="65" />
                <el-table-column label="应收金额" min-width="115"><template #default="{ row }">¥ {{ money(row.plannedAmount) }}</template></el-table-column>
                <el-table-column label="已到账" min-width="115"><template #default="{ row }">¥ {{ money(row.receivedAmount) }}</template></el-table-column>
                <el-table-column label="本期历史余额" min-width="150"><template #default="{ row }">¥ {{ money(row.outstandingAmount) }}<div v-if="row.replacedByPlanId" class="scope-note">已由新版承接，不重复应收</div></template></el-table-column>
                <el-table-column prop="dueDate" label="到期日" min-width="110" />
                <el-table-column label="状态" width="105"><template #default="{ row }">{{ { UNPAID: '未到账', PARTIAL: '部分到账', PAID: '已到账', OVERDUE: '已逾期' }[row.periodStatus] || row.periodStatus }}</template></el-table-column>
                <el-table-column label="操作" width="105"><template #default="{ row }"><el-button v-if="plan.current && plan.approvalStatus === 'ACTIVE' && payments.canRegisterReceipt && row.outstandingAmount > 0" link type="primary" @click="openReceipt(row)">登记到账</el-button></template></el-table-column>
              </el-table>
            </section>
            </DetailSection><DetailSection title="实际到账记录">
            <el-table :data="payments.receipts" size="small" empty-text="暂无到账记录">
              <el-table-column type="expand"><template #default="{ row }"><el-descriptions :column="1" border><el-descriptions-item label="计划 / 期次">{{ payments.plans.map(plan => { const period = plan.periods.find(item => item.id === row.periodId); return period ? `第${plan.versionNo}版 / 第${period.periodNo}期` : ''; }).filter(Boolean).join('') }}</el-descriptions-item><el-descriptions-item label="到账方式">{{ { BANK_TRANSFER: '银行转账', CASH: '现金', ONLINE: '线上支付', OTHER: '其他' }[row.paymentMethod] || '未填写' }}</el-descriptions-item><el-descriptions-item label="差异说明">{{ row.differenceDescription || '无' }}</el-descriptions-item><el-descriptions-item label="备注">{{ row.remark || '无' }}</el-descriptions-item><el-descriptions-item v-if="row.voided" label="作废人 / 时间">{{ row.voidedByName }} · {{ time(row.voidedAt) }}</el-descriptions-item></el-descriptions></template></el-table-column>
              <el-table-column label="到账金额" width="130"><template #default="{ row }">¥ {{ money(row.amount) }}</template></el-table-column>
              <el-table-column prop="receivedAt" label="到账时间" min-width="170" />
              <el-table-column prop="registeredByName" label="登记人" width="110" />
              <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.voided ? 'danger' : 'success'">{{ row.voided ? '已作废' : '有效' }}</el-tag></template></el-table-column>
              <el-table-column prop="voidReason" label="作废原因" min-width="140" />
              <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="payments.canVoidReceipt && !row.voided" link type="danger" @click="voidReceipt(row)">作废误录</el-button></template></el-table-column>
            </el-table></DetailSection>
          </template>
          <el-empty v-else description="回款数据暂不可用" />
        </div>
</el-tab-pane><el-tab-pane label="文件版本" name="files"><DetailSection title="实际签署文件" description="以最新版本核验，历史版本保留可查。">
        <p class="scope-note">
          允许上传多个签署文件版本；核验最新一版，所有历史文件保留可查。合同登记表不要求草稿文件。
        </p>
        <PagedItems :items="detail.attachments" v-slot="{ rows: pageRows }">
          <el-table :data="pageRows" empty-text="尚未上传签署资料">
            <el-table-column label="文件" min-width="180"
              ><template #default="{ row }"
                >{{ row.fileName }}
                <el-tag v-if="row.id === latestSignedId" size="small" type="info"
                  >最新签署版</el-tag
                >
              </template></el-table-column
            >
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column label="资料类型" width="130"
              ><template #default="{ row }">{{
                {
                  CONTRACT_DRAFT: '历史草稿文件',
                  SIGNED_CONTRACT: '实际签署合同',
                  SUPPLEMENT: '补充资料',
                }[row.materialType] || row.materialType
              }}</template></el-table-column
            >
            <el-table-column label="核验" width="90"
              ><template #default="{ row }">{{
                { PENDING: '待核验', PASSED: '已通过', REJECTED: '已退回' }[row.confirmStatus] ||
                row.confirmStatus
              }}</template></el-table-column
            >
            <el-table-column prop="uploaderName" label="上传人" width="110" />
            <el-table-column prop="confirmOpinion" label="核验意见" min-width="140" />
            <el-table-column label="上传时间" min-width="175"
              ><template #default="{ row }">{{ time(row.uploadedAt) }}</template></el-table-column
            >
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="download(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PagedItems>
</DetailSection></el-tab-pane><el-tab-pane label="历史记录" name="history"><DetailSection title="签署进度历史">
        <PagedItems :items="detail.signingHistory" v-slot="{ rows: pageRows }">
          <el-timeline>
            <el-timeline-item
              v-for="item in pageRows"
              :key="item.id"
              :timestamp="time(item.changedAt)"
            >
              <strong>{{ progressLabels[item.toProgress] || item.toProgress }}</strong>
              <p>
                {{
                  item.fromProgress
                    ? `${progressLabels[item.fromProgress] || item.fromProgress} → `
                    : ''
                }}{{ item.reason }}<span v-if="item.correction">（管理人员恢复）</span>
              </p>
              <p>
                操作人：{{ item.operatorName }} ·
                {{
                  { ADMIN: '管理员', MANAGER: '团队经理', SPECIALIST: '专员', SYSTEM: '系统' }[
                    item.operatorRole
                  ] || item.operatorRole
                }}
              </p>
            </el-timeline-item>
          </el-timeline>
        </PagedItems>
        <el-empty v-if="!detail.signingHistory?.length" description="暂无签署进度历史" />
</DetailSection><DetailSection title="合同操作记录">
        <PagedItems :items="detail.operationHistory" v-slot="{ rows: pageRows }">
          <el-timeline>
            <el-timeline-item
              v-for="item in pageRows"
              :key="item.id"
              :timestamp="time(item.occurredAt)"
            >
              <strong>{{ item.reason || item.actionCode }}</strong>
              <p>操作人：{{ item.operatorName || '系统任务' }}</p>
            </el-timeline-item>
          </el-timeline>
        </PagedItems>
        <el-empty v-if="!detail.operationHistory?.length" description="暂无操作记录" /></DetailSection></el-tab-pane></el-tabs>
      </template>
    </el-drawer>
    <el-dialog v-model="paymentDialog" title="新建回款计划版本" width="min(760px, 94%)">
      <el-form label-position="top">
        <el-form-item v-if="payments?.plans?.some((item) => item.current)" label="换版差异说明" required>
          <el-input v-model="paymentPlanForm.changeDescription" type="textarea" :rows="2" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-table :data="paymentPlanForm.periods" size="small">
          <el-table-column label="期次" width="70"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column>
          <el-table-column label="金额（元）" min-width="150"><template #default="{ row }"><el-input-number v-model="row.plannedAmount" :min="0" :precision="2" :step="100" controls-position="right" /></template></el-table-column>
          <el-table-column label="到期日" min-width="170"><template #default="{ row }"><el-date-picker v-model="row.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择到期日" /></template></el-table-column>
          <el-table-column label="触发节点" min-width="140"><template #default="{ row }"><el-select v-model="row.triggerNode"><el-option label="签约" value="SIGNING" /><el-option label="交付" value="DELIVERY" /><el-option label="验收" value="ACCEPTANCE" /><el-option label="质保期" value="WARRANTY" /><el-option label="其他" value="OTHER" /></el-select></template></el-table-column>
          <el-table-column label="移除" width="70"><template #default="{ $index }"><el-button link type="danger" :disabled="paymentPlanForm.periods.length <= 1" @click="paymentPlanForm.periods.splice($index, 1)">移除</el-button></template></el-table-column>
        </el-table>
        <p class="scope-note">计划总额必须等于合同未收余额：¥ {{ money(payments?.outstandingAmount ?? detail?.contractAmount) }}</p>
        <el-button @click="addPaymentPeriod">添加一期</el-button>
      </el-form>
      <template #footer><el-button @click="paymentDialog = false">取消</el-button><el-button type="primary" :loading="paymentSaving" @click="savePaymentPlan">保存计划草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="receiptDialog" title="登记实际到账" width="min(560px, 94%)">
      <el-form label-position="top">
        <el-form-item label="到账金额（元）" required><el-input-number v-model="receiptForm.amount" :min="0.01" :max="payments?.outstandingAmount" :precision="2" :step="100" controls-position="right" /></el-form-item>
        <el-form-item label="实际到账时间" required><el-date-picker v-model="receiptForm.receivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择北京时间" /></el-form-item>
        <el-form-item label="到账方式"><el-select v-model="receiptForm.paymentMethod"><el-option label="银行转账" value="BANK_TRANSFER" /><el-option label="现金" value="CASH" /><el-option label="线上支付" value="ONLINE" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="差异说明（未收完时必填）"><el-input v-model="receiptForm.differenceDescription" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="receiptForm.remark" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="receiptDialog = false">取消</el-button><el-button type="primary" :loading="paymentSaving" @click="saveReceipt">登记到账</el-button></template>
    </el-dialog>
    <el-dialog
      v-model="dialogOpen"
      :title="dialog === 'edit' ? '编辑合同登记' : '上传合同'"
      width="min(780px, 94%)"
      top="5vh"
      append-to-body
      body-class="contract-editor-body"
      destroy-on-close
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form
        ref="editorForm"
        class="contract-registration-form"
        :model="form"
        label-position="top"
        :scroll-into-view-options="{ block: 'center' }"
        @submit.prevent="save()"
      >
        <el-alert
          v-if="dialog === 'edit' && detail?.canSubmitDraft"
          type="info"
          :closable="false"
          title="可先保存未填完的草稿；标星内容在提交核对前必填。已填写的电话、信用代码及日期需符合格式，不需要草稿文件。"
          class="form-error"
        />
        <template v-if="dialog === 'edit'">
          <h3 class="registration-section">基本信息</h3>
          <div class="registration-grid">
            <el-form-item label="内部登记编号（系统生成）"
              ><el-input :validate-event="false" :model-value="detail.contractNo" disabled
            /></el-form-item>
            <el-form-item
              label="正式合同编号（未确定可先留空）"
              prop="externalContractNo"
              :error="fieldErrors.externalContractNo"
              ><el-input :validate-event="false" v-model="form.externalContractNo" maxlength="64"
            /></el-form-item>
            <el-form-item
              label="合同名称"
              prop="contractName"
              :error="fieldErrors.contractName"
              required
              ><el-input :validate-event="false" v-model="form.contractName" maxlength="160"
            /></el-form-item>
            <el-form-item
              label="业务类型"
              prop="businessType"
              :error="fieldErrors.businessType"
              required
              ><el-input
                :validate-event="false"
                v-model="form.businessType"
                maxlength="64"
                placeholder="软件开发、设备销售、咨询服务等"
            /></el-form-item>
            <el-form-item
              label="合同金额（元）"
              prop="contractAmount"
              :error="fieldErrors.contractAmount"
              required
              ><el-input-number
                :validate-event="false"
                v-model="form.contractAmount"
                :min="0"
                :precision="2"
                controls-position="right"
            /></el-form-item>
            <el-form-item
              label="预计签订日期"
              prop="plannedSignedAt"
              :error="fieldErrors.plannedSignedAt"
              ><el-date-picker
                :validate-event="false"
                v-model="form.plannedSignedAt"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
          </div>
          <h3 class="registration-section">我方信息</h3>
          <div class="registration-grid">
            <el-form-item
              label="我方公司／签约主体"
              prop="ourPartyName"
              :error="fieldErrors.ourPartyName"
              required
              ><el-input :validate-event="false" v-model="form.ourPartyName" maxlength="160"
            /></el-form-item>
            <el-form-item
              label="我方统一社会信用代码（选填）"
              prop="ourCreditCode"
              :error="fieldErrors.ourCreditCode"
              ><el-input
                :validate-event="false"
                v-model="form.ourCreditCode"
                maxlength="18"
                placeholder="可留空；填写时须为18位字母或数字"
              />
              <p class="field-help">选填；填写时须为18位字母或数字，小写自动转大写。</p>
            </el-form-item>
            <el-form-item
              label="我方联系人"
              prop="ourContactName"
              :error="fieldErrors.ourContactName"
              required
              ><el-input :validate-event="false" v-model="form.ourContactName" maxlength="64"
            /></el-form-item>
            <el-form-item
              label="我方联系电话"
              prop="ourContactPhone"
              :error="fieldErrors.ourContactPhone"
              required
              ><el-input :validate-event="false" v-model="form.ourContactPhone" maxlength="40"
            /></el-form-item>
            <el-form-item
              label="我方地址"
              prop="ourAddress"
              :error="fieldErrors.ourAddress"
              class="full-width"
              ><el-input :validate-event="false" v-model="form.ourAddress" maxlength="255"
            /></el-form-item>
          </div>
          <h3 class="registration-section">对方信息</h3>
          <div class="registration-grid">
            <el-form-item
              label="对方公司／签约主体"
              prop="counterpartyName"
              :error="fieldErrors.counterpartyName"
              required
              ><el-input :validate-event="false" v-model="form.counterpartyName" maxlength="160"
            /></el-form-item>
            <el-form-item
              label="对方统一社会信用代码（选填）"
              prop="counterpartyCreditCode"
              :error="fieldErrors.counterpartyCreditCode"
              ><el-input
                :validate-event="false"
                v-model="form.counterpartyCreditCode"
                maxlength="18"
                placeholder="可留空；填写时须为18位字母或数字"
              />
              <p class="field-help">选填；填写时须为18位字母或数字，小写自动转大写。</p>
            </el-form-item>
            <el-form-item
              label="对方联系人"
              prop="counterpartyContactName"
              :error="fieldErrors.counterpartyContactName"
              required
              ><el-input
                :validate-event="false"
                v-model="form.counterpartyContactName"
                maxlength="64"
            /></el-form-item>
            <el-form-item
              label="对方联系电话"
              prop="counterpartyContactPhone"
              :error="fieldErrors.counterpartyContactPhone"
              required
              ><el-input
                :validate-event="false"
                v-model="form.counterpartyContactPhone"
                maxlength="40"
            /></el-form-item>
            <el-form-item
              label="对方地址"
              prop="counterpartyAddress"
              :error="fieldErrors.counterpartyAddress"
              class="full-width"
              ><el-input :validate-event="false" v-model="form.counterpartyAddress" maxlength="255"
            /></el-form-item>
          </div>
          <h3 class="registration-section">业务内容和履行安排</h3>
          <el-form-item
            label="合同标的／业务内容"
            prop="description"
            :error="fieldErrors.description"
            required
            ><el-input
              :validate-event="false"
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
          /></el-form-item>
          <el-form-item
            label="付款约定（仅登记，不自动生成回款计划）"
            prop="paymentTerms"
            :error="fieldErrors.paymentTerms"
            ><el-input
              :validate-event="false"
              v-model="form.paymentTerms"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
          /></el-form-item>
          <el-form-item
            label="交付或验收约定"
            prop="deliveryTerms"
            :error="fieldErrors.deliveryTerms"
            ><el-input
              :validate-event="false"
              v-model="form.deliveryTerms"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
          /></el-form-item>
          <div class="registration-grid">
            <el-form-item
              label="履行开始日期"
              prop="performanceStartDate"
              :error="fieldErrors.performanceStartDate"
              ><el-date-picker
                :validate-event="false"
                v-model="form.performanceStartDate"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
            <el-form-item label="履行截止日期" prop="expireDate" :error="fieldErrors.expireDate"
              ><el-date-picker
                :validate-event="false"
                v-model="form.expireDate"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
            <el-form-item
              label="实际签订日期（实际签署后填写）"
              prop="signedAt"
              :error="fieldErrors.signedAt"
              ><el-date-picker
                :validate-event="false"
                v-model="form.signedAt"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
          </div>
        </template>
        <template v-else>
          <el-form-item label="实际签署合同文件" required>
            <input type="file" accept=".pdf,.doc,.docx,application/pdf" @change="chooseFile" />
            <p class="scope-note">
              支持 PDF、Word，单个文件不超过20MB；文件作为版本保存，不覆盖历史资料。
            </p>
          </el-form-item>
          <el-form-item label="文件说明">
            <el-input :validate-event="false" v-model="form.note" maxlength="500" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          :closable="false"
          show-icon
          class="submit-error"
          aria-live="polite"
        />
        <el-button :disabled="saving" @click="dialogOpen = false">取消</el-button>
        <el-button
          v-if="dialog === 'edit' && detail?.canSubmitDraft"
          type="primary"
          :loading="saving"
          @click="save(true)"
        >
          保存并提交登记核对
        </el-button>
        <el-button type="primary" :loading="saving" @click="save()">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="progressDialog"
      :title="'异常、撤回与恢复'"
      append-to-body
      width="min(520px, 94%)"
      :close-on-click-modal="!saving"
    >
      <el-alert
        v-if="detail?.canCorrectSigningProgress"
        type="warning"
        :closable="false"
        title="恢复到暂停前步骤并重新核对材料；正常进度必须通过上传、提交和核验推进。"
        class="form-error"
      />
      <el-form label-position="top">
        <el-form-item label="当前进度">
          <el-input :model-value="progressLabels[detail?.signingProgress] || ''" disabled />
        </el-form-item>
        <el-form-item label="调整为" required>
          <el-select v-model="progressForm.targetProgress" placeholder="选择签署进度">
            <el-option
              v-for="item in progressTargets"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="progressForm.targetProgress === 'WITHDRAWN'"
          type="info"
          :closable="false"
          title="撤回后暂停办理，保留文件和审批结果；经理或管理员可核对条件后恢复原步骤。"
          class="form-error"
        />
        <el-form-item label="操作原因" required>
          <el-input v-model="progressForm.reason" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="progressDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!progressForm.targetProgress || !progressForm.reason.trim()"
          @click="saveProgress"
          >确认更新</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:global(.contract-editor-body) {
  max-height: 65vh;
  overflow-y: auto;
}
.registration-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.registration-grid .full-width {
  grid-column: 1 / -1;
}
.registration-section {
  margin: 16px 0 14px;
  color: var(--crm-text);
  font-size: 15px;
}

.toolbar {
  display: flex;
  gap: 12px;
  padding: 18px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.toolbar .el-input {
  width: 260px;
}
.toolbar .el-select {
  width: 180px;
}
.scope-note {
  color: var(--crm-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.current-progress {
  margin: 10px 0 0;
}
.current-progress strong {
  color: var(--crm-primary);
}
.signing-steps {
  margin: 20px 0 14px;
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
.payment-panel {
  min-height: 120px;
}
.payment-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--crm-border);
}
.payment-summary > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.payment-summary span {
  color: var(--crm-text-secondary);
  font-size: 12px;
}
.payment-summary strong {
  font-size: 15px;
  overflow-wrap: anywhere;
}
.payment-plan {
  padding: 14px 0;
  border-bottom: 1px solid var(--crm-border);
}
.payment-plan-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.payment-plan-head > span {
  margin-left: auto;
  color: var(--crm-text-secondary);
  font-size: 13px;
}
.payment-actions {
  margin: 12px 0;
}
.el-input-number,
.el-date-editor,
.el-select {
  width: 100%;
}
.form-error {
  margin-bottom: 16px;
}
.submit-error {
  margin-bottom: 12px;
  text-align: left;
}
.contract-registration-form :deep(.el-form-item__error) {
  position: static;
  width: 100%;
  padding-top: 4px;
  line-height: 1.5;
}
.field-help {
  margin: 4px 0 0;
  color: var(--crm-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.el-timeline {
  padding: 12px 8px 0 16px;
}
.el-timeline p {
  color: var(--crm-text-secondary);
  font-size: 13px;
}
@media (max-width: 600px) {
  .registration-grid {
    grid-template-columns: 1fr;
  }
  .toolbar .el-input,
  .toolbar .el-select {
    width: 100%;
  }
  .toolbar {
    padding: 14px;
  }
  .signing-steps {
    overflow-x: auto;
  }
  .payment-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .payment-plan-head > span {
    width: 100%;
    margin-left: 0;
  }
}
</style>
