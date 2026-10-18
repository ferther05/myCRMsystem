export const ROLE_NAMES = { ADMIN: '系统管理员', MANAGER: '销售经理', SPECIALIST: '销售专员' }

// Only application-internal, non-auth destinations can be restored after login.
export function safeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')
      || /[\\\r\n]/.test(value) || /%2f|%5c|%0[ad]/i.test(value)) return '/workbench'
  const path = value.split(/[?#]/)[0]
  if (['/login', '/change-password', '/403', '/404'].includes(path)) return '/workbench'
  return value
}

export function validNewPassword(value) {
  return typeof value === 'string' && value.length >= 8 && value.length <= 64
    && /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$/.test(value)
}

export function validLoginPassword(value) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= 72
    && new TextEncoder().encode(value).length <= 72
}
