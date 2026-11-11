/* 文件作用：核对维护计划取舍、时区、到点提示和历史联系人展示排序。 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { customerRelationship, maintenanceTiming, relationshipTime } from './customer-relationship.js';

test('打回互动保留在近期记录，但不成为下一步维护依据', () => {
  const valid = { id: 1, followedAt: '2026-11-03T09:00:00', nextAction: '联系财务确认付款安排', nextActionAt: '2026-11-04T10:00:00', reviewStatus: 'CONFIRMED' };
  const returned = { id: 2, followedAt: '2026-11-03T11:00:00', nextAction: '已打回的安排', nextActionAt: '2026-11-05T10:00:00', reviewStatus: 'RETURNED' };
  const summary = customerRelationship({ followRecords: [valid, returned] });
  assert.equal(summary.interactions[0].id, 2);
  assert.equal(summary.plan.id, 1);
});

test('最近记录缺少计划时不把旧安排重新显示为当前计划', () => {
  const summary = customerRelationship({ followRecords: [
    { id: 1, followedAt: '2026-11-01T09:00:00', nextAction: '旧安排', nextActionAt: '2026-11-02T10:00:00' },
    { id: 2, followedAt: '2026-11-03T09:00:00', nextAction: '', nextActionAt: null },
  ] });
  assert.equal(summary.plan, null);
  assert.equal(customerRelationship({ followRecords: [{ ...summary.interactions[0], nextAction: '补资料', nextActionAt: '错误时间' }] }).plan, null);
});

test('时间阈值统一按北京时间，时间已到不等于计划已完成', () => {
  const plan = { nextActionAt: '2026-11-03T10:00:00' };
  const deadline = Date.parse('2026-11-03T02:00:00Z');
  assert.equal(relationshipTime(plan.nextActionAt), deadline);
  assert.equal(maintenanceTiming(plan, deadline - 1), '按计划联系');
  assert.equal(maintenanceTiming(plan, deadline), '时间已到，请确认进展');
  assert.equal(maintenanceTiming(null, deadline), '尚未安排');
});

test('主要联系人优先，其次决策和财务；近期仅三条且不改变原详情', () => {
  const customer = { contacts: [{ id: 1, decisionRole: 'USER' }, { id: 2, decisionRole: 'FINANCE' }, { id: 3, decisionRole: 'DECISION_MAKER' }, { id: 4, decisionRole: 'CONTACT', primary: true }], followRecords: [1, 2, 3, 4].map(id => ({ id, followedAt: '2026-11-03T10:00:00' })) };
  const summary = customerRelationship(customer);
  assert.deepEqual(summary.contacts.map(c => c.id), [4, 3, 2, 1]);
  assert.deepEqual(summary.interactions.map(f => f.id), [4, 3, 2]);
  assert.deepEqual(customer.contacts.map(c => c.id), [1, 2, 3, 4]);
  assert.deepEqual(customerRelationship(null), { contacts: [], interactions: [], plan: null });
});
