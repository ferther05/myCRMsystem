import test from 'node:test'
import assert from 'node:assert/strict'
import { validUsername, validPhone, validInitialPassword } from './account-rules.js'

test('账号字符、长度边界与用户名格式', () => {
  for (const value of ['east.sales', 'user_01', 'A-1', 'a'.repeat(64)]) assert.equal(validUsername(value), true)
  for (const value of ['ab', '.sales', '中文用户', 'white space', 'a'.repeat(65)]) assert.equal(validUsername(value), false)
})
test('初始密码遵循后端的 ASCII、字母数字及长度规则', () => {
  for (const value of ['Abcdef12', 'Example123!', 'A1'+'x'.repeat(62)]) assert.equal(validInitialPassword(value), true)
  for (const value of ['12345678', 'abcdefgh', 'Abc123', '密码123456', 'Space 1234', 'A1'+'x'.repeat(63)]) assert.equal(validInitialPassword(value), false)
})
test('联系电话允许清空并拒绝无效字符', () => {
  for (const value of ['', null, '+86 13812345678', '010-88886666']) assert.equal(validPhone(value), true)
  for (const value of ['abc123456', '12345', '中文电话', '1'.repeat(33)]) assert.equal(validPhone(value), false)
})
