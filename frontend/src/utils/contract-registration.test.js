/* 文件作用：验证合同登记输入边界及保存与送审的不同要求。 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { registrationErrors, registrationPayload } from './contract-registration.js';

/** 提供可以先保存的最小草稿。 */
function draft(extra = {}) {
  return registrationPayload({
    version: 0,
    contractName: '测试登记',
    contractAmount: 1200.5,
    ...extra,
  });
}

test('草稿可保存不完整资料，送审需要双方及业务信息', () => {
  assert.deepEqual(registrationErrors(draft()), {});
  assert.ok(registrationErrors(draft(), true).ourContactName);
  assert.ok(registrationErrors(draft(), true).description);
  assert.equal(draft().signedAt, null);
});

test('信用代码可留空，填写需18位且规范空格与小写', () => {
  assert.equal(registrationErrors(draft({ ourCreditCode: '' })).ourCreditCode, undefined);
  assert.ok(registrationErrors(draft({ ourCreditCode: '11122' })).ourCreditCode);
  const payload = draft({
    ourCreditCode: ' 91310000abcdefgh12 ',
    counterpartyCreditCode: '91310000ABCDEFGH12',
  });
  assert.equal(payload.ourCreditCode, '91310000ABCDEFGH12');
  assert.deepEqual(registrationErrors(payload), {});
  assert.ok(registrationErrors(draft({ ourCreditCode: '91310000ABCDEFGH1中' })).ourCreditCode);
});

test('电话支持座机国际格式，拒绝文字及过短输入', () => {
  assert.deepEqual(registrationErrors(draft({ ourContactPhone: '+86 (021) 1234-5678' })), {});
  assert.ok(registrationErrors(draft({ ourContactPhone: '电话123' })).ourContactPhone);
  assert.ok(registrationErrors(draft({ counterpartyContactPhone: '12' })).counterpartyContactPhone);
});

test('相同履行和签订日期允许，错误日期和倒序日期明确提示', () => {
  const same = {
    performanceStartDate: '2026-10-31',
    expireDate: '2026-10-31',
    signedAt: '2026-10-31',
  };
  assert.deepEqual(registrationErrors(draft(same)), {});
  assert.ok(registrationErrors(draft({ plannedSignedAt: '2026-02-30' })).plannedSignedAt);
  assert.ok(registrationErrors(draft({ ...same, signedAt: '2026-11-01' })).signedAt);
  assert.ok(
    registrationErrors(draft({ ...same, performanceStartDate: '2026-11-01' })).performanceStartDate,
  );
});

test('金额与字段长度不合法时保存也必须拒绝', () => {
  for (const amount of [null, -1, 1.001, Number.NaN]) {
    assert.ok(registrationErrors(draft({ contractAmount: amount })).contractAmount);
  }
  assert.deepEqual(registrationErrors(draft({ contractAmount: 0 })), {});
  assert.ok(registrationErrors(draft({ contractName: '字'.repeat(161) })).contractName);
});

test('完整送审可不填写实际签订日期，且不发送其他弹窗残留字段', () => {
  const payload = draft({
    businessType: '软件服务',
    ourPartyName: '我方公司',
    counterpartyName: '客户公司',
    ourContactName: '我方联系人',
    ourContactPhone: '13900000001',
    counterpartyContactName: '对方联系人',
    counterpartyContactPhone: '13800000001',
    description: '服务内容',
    note: '上传弹窗的残留字段',
  });
  assert.deepEqual(registrationErrors(payload, true), {});
  assert.equal(payload.signedAt, null);
  assert.equal(payload.note, undefined);
});
