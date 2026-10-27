export const customerStatuses = {
  NEW: '待首联', FOLLOWING: '跟进中', WARNING: '预警中', PROTECTED: '保护中',
  DEAL_PENDING: '成交待结清', DEAL: '已成交', CLOSED: '已关闭', LOST: '流失', FROZEN: '冻结中',
}
export const contactRoles = { CONTACT: '业务联系人', DECISION_MAKER: '决策人', USER: '使用人', FINANCE: '财务对接人', INFLUENCER: '影响人' }
const customerLabels = {
  level: { KEY: '重点客户', NORMAL: '常规客户', WATCH: '观望客户' },
  tag: { POTENTIAL: '高潜力', LONG_TERM: '长期合作', PRICE_SENSITIVE: '价格敏感', LOCAL: '本地客户', NEW_DEMAND: '新增需求' },
}
export function customerDictionaryLabel(rows, group, code) {
  if (!code) return '未填写'
  const configured = rows.find((row) => row.group === group && row.code === code)?.label
  if (configured) return configured
  if (customerLabels[group]?.[code]) return customerLabels[group][code]
  if (/[\u3400-\u9fff]/u.test(code)) return code
  return group === 'level' ? '未配置等级' : group === 'tag' ? '未配置标签' : code
}
export const emptyProfile = () => ({ customerName: '', unifiedSocialCreditCode: '', sourceCode: '', industryCode: '', region: '', companySize: '', remark: '', customerLevel: 'NORMAL', tags: [] })
export const emptyContact = () => ({ contactName: '', jobTitle: '', phone: '', email: '', birthday: null, decisionRole: 'CONTACT', primary: false })
export function profileFrom(detail) {
  const profile = emptyProfile()
  for (const key of Object.keys(profile)) if (detail[key] != null) profile[key] = Array.isArray(detail[key]) ? [...detail[key]] : detail[key]
  return profile
}
export function customerFilterParams(filters) {
  const params = {}
  for (const [key, value] of Object.entries(filters)) {
    if (Array.isArray(value)) { if (value.length) params[key] = value.join(',') }
    else if (value !== '' && value != null) params[key] = typeof value === 'string' ? value.trim() : value
  }
  return params
}
