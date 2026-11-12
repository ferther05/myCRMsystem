/* 文件作用：验证新增标识的北京时间、72小时、未来和异常输入边界。 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { isRecentRecord } from './recent-record.js';
test('新增只按创建后72小时，兼容秒毫秒和明确时区', () => {
  const now = Date.parse('2026-11-11T12:00:00+08:00');
  for (const value of ['2026-11-11T12:00:00', '2026-11-08 12:00:00.001', '2026-11-11T04:00:00Z']) assert.equal(isRecentRecord(value, now), true);
  for (const value of [null, '', 'invalid', '2026-11-08T12:00:00', '2026-11-11T12:00:00.001', '2026-11-07T12:00:00']) assert.equal(isRecentRecord(value, now), false);
});
