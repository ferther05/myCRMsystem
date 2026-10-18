export const ACCOUNT_STATUS = { ACTIVE: '已启用', DISABLED: '已停用', PENDING: '待审核开通' }
export const APPROVAL_STATUS = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消' }
export const validUsername = (value) => /^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/.test(value)
export const validPhone = (value) => !value || /^[0-9+() -]{6,32}$/.test(value)
export const validInitialPassword = (value) => /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$/.test(value)
export const check = (test, message) => (_rule, value, callback) => test(value) ? callback() : callback(new Error(message))
