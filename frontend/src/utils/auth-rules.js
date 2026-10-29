/* 文件作用：提供角色名称、安全跳转和密码校验。 */
export const ROLE_NAMES = { ADMIN: '系统管理员', MANAGER: '销售经理', SPECIALIST: '销售专员' };

// 登录后仅恢复站内业务页面，避免跳到外站或循环进入认证页面。
/** 仅允许安全的站内跳转地址。 */
export function safeRedirect(value) {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    /[\\\r\n]/.test(value) ||
    /%2f|%5c|%0[ad]/i.test(value)
  )
    return '/workbench';
  const path = value.split(/[?#]/)[0];
  if (['/login', '/change-password', '/403', '/404'].includes(path)) return '/workbench';
  return value;
}

/** 校验新密码的长度、字符及复杂度。 */
export function validNewPassword(value) {
  return (
    typeof value === 'string' &&
    value.length >= 8 &&
    value.length <= 64 &&
    /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$/.test(value)
  );
}

/** 校验登录密码输入的有效长度。 */
export function validLoginPassword(value) {
  return (
    typeof value === 'string' &&
    value.trim().length > 0 &&
    value.length <= 72 &&
    new TextEncoder().encode(value).length <= 72
  );
}
