/* 文件作用：统一读写浏览器中的登录令牌。 */
export const TOKEN_KEY = 'crm.access-token';

/** 读取浏览器保存的登录令牌。 */
export function readToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

/** 保存当前登录令牌。 */
export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

/** 删除浏览器中的登录令牌。 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}
