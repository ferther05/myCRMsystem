/* 文件作用：以业务时区判断创建时间是否处于最近72小时，忽略未来和无效时间。 */
export function isRecentRecord(value, now = Date.now()) {
  if (typeof value !== 'string' || !value.trim()) return false;
  const text = value.trim().replace(' ', 'T');
  const timestamp = Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(text) ? text : `${text}+08:00`);
  const elapsed = now - timestamp;
  return Number.isFinite(timestamp) && elapsed >= 0 && elapsed < 72 * 60 * 60 * 1000;
}
