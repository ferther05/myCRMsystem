/* 文件作用：统一合同登记表的输入整理和保存、送审校验。 */
const fields = {
  contractName: ['合同名称', 160],
  externalContractNo: ['正式合同编号', 64],
  businessType: ['业务类型', 64],
  ourPartyName: ['我方公司', 160],
  counterpartyName: ['对方公司', 160],
  ourContactName: ['我方联系人', 64],
  counterpartyContactName: ['对方联系人', 64],
  ourContactPhone: ['我方联系电话', 40],
  counterpartyContactPhone: ['对方联系电话', 40],
  ourAddress: ['我方地址', 255],
  counterpartyAddress: ['对方地址', 255],
  description: ['合同标的／业务内容', 1000],
  paymentTerms: ['付款约定', 1000],
  deliveryTerms: ['交付或验收约定', 1000],
};
const dates = {
  plannedSignedAt: '预计签订日期',
  performanceStartDate: '履行开始日期',
  expireDate: '履行截止日期',
  signedAt: '实际签订日期',
};
const requiredForSubmit = [
  'contractName',
  'businessType',
  'ourPartyName',
  'counterpartyName',
  'ourContactName',
  'ourContactPhone',
  'counterpartyContactName',
  'counterpartyContactPhone',
  'description',
];

/** 只提交登记可编辑字段，去掉首尾空格、规范信用代码和未填日期。 */
export function registrationPayload(form) {
  const payload = { version: form.version, contractAmount: form.contractAmount };
  for (const key of Object.keys(fields)) payload[key] = String(form[key] ?? '').trim();
  for (const key of ['ourCreditCode', 'counterpartyCreditCode']) {
    payload[key] = String(form[key] ?? '')
      .trim()
      .toUpperCase();
  }
  for (const key of Object.keys(dates)) payload[key] = form[key] || null;
  return payload;
}

/** 检查真实公历日期，避免日期组件之外传入的无效字符串。 */
function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** 保存允许暂缺送审资料，但已填写的格式及日期关系始终必须正确。 */
export function registrationErrors(payload, submit = false) {
  const errors = {};
  for (const [key, [label, max]] of Object.entries(fields)) {
    if ((key === 'contractName' || (submit && requiredForSubmit.includes(key))) && !payload[key]) {
      errors[key] = `请填写${label}`;
    } else if (payload[key]?.length > max) errors[key] = `${label}最多${max}字`;
  }
  const amount = String(payload.contractAmount ?? '');
  if (!/^\d{1,16}(?:\.\d{1,2})?$/.test(amount)) {
    errors.contractAmount = '请填写非负合同金额，整数最多16位、小数最多2位';
  }
  for (const key of ['ourContactPhone', 'counterpartyContactPhone']) {
    if (payload[key] && !/^[0-9+() -]{3,40}$/.test(payload[key])) {
      errors[key] = `${fields[key][0]}需为3～40位数字、空格、加减号或括号`;
    }
  }
  for (const [key, label] of [
    ['ourCreditCode', '我方'],
    ['counterpartyCreditCode', '对方'],
  ]) {
    if (payload[key] && !/^[0-9A-Z]{18}$/.test(payload[key])) {
      errors[key] = `${label}统一社会信用代码须为18位大写字母或数字，也可留空`;
    }
  }
  for (const [key, label] of Object.entries(dates)) {
    if (payload[key] && !validDate(payload[key])) errors[key] = `${label}不是有效日期`;
  }
  if (payload.expireDate && !errors.expireDate) {
    if (
      payload.performanceStartDate &&
      !errors.performanceStartDate &&
      payload.performanceStartDate > payload.expireDate
    ) {
      errors.performanceStartDate = '履行开始日期不能晚于履行截止日期';
    }
    if (payload.signedAt && !errors.signedAt && payload.signedAt > payload.expireDate) {
      errors.signedAt = '实际签订日期不能晚于履行截止日期';
    }
  }
  return errors;
}
