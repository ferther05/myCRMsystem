/* 文件作用：提供账号资料校验及表单校验适配器。 */
export const ACCOUNT_STATUS = { ACTIVE: '已启用', DISABLED: '已停用', PENDING: '待审核开通' };
export const APPROVAL_STATUS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
};
/** 校验账号长度、首字符和允许字符。 */
export const validUsername = (value) => /^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/.test(value);
/** 校验可选联系电话格式。 */
export const validPhone = (value) => !value || /^[0-9+() -]{6,32}$/.test(value);
/** 校验初始密码长度及字母数字要求。 */
export const validInitialPassword = (value) => /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$/.test(value);
/** 将布尔校验函数转换为 Element Plus 表单校验器。 */
export const check =
  (test, message) => /* 执行字段规则并通过表单回调返回校验结果。 */ (_rule, value, callback) =>
    test(value) ? callback() : callback(new Error(message));
