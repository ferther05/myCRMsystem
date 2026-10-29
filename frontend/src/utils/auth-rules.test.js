/* 文件作用：验证安全跳转及登录、新密码的边界规则。 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { safeRedirect, validNewPassword, validLoginPassword } from './auth-rules.js';

test('login redirects remain internal and never loop into auth screens', /* 验证跳转仅限站内且不循环进入认证页面。 */ () => {
  for (const invalid of [
    null,
    [],
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/%2fexample.com',
    '/login',
    '/change-password?redirect=/login',
    '/workbench\n',
  ]) {
    assert.equal(safeRedirect(invalid), '/workbench');
  }
  assert.equal(safeRedirect('/customers?keyword=中文'), '/customers?keyword=中文');
  assert.equal(safeRedirect('/account'), '/account');
});

test('new password matches backend boundaries and character rules', /* 验证新密码边界及字符规则与后端一致。 */ () => {
  assert.equal(validNewPassword('Ab123456'), true);
  assert.equal(validNewPassword('A' + '1'.repeat(63)), true);
  for (const invalid of [
    'A123456',
    'A' + '1'.repeat(64),
    'abcdefgh',
    '12345678',
    'Password 123',
    '密码Password123',
    'Password123\n',
  ])
    assert.equal(validNewPassword(invalid), false);
});

test('login does not allow BCrypt truncation for multibyte input', /* 验证多字节登录密码不触发 BCrypt 截断。 */ () => {
  assert.equal(validLoginPassword('A'.repeat(72)), true);
  assert.equal(validLoginPassword('汉'.repeat(24)), true);
  assert.equal(validLoginPassword('汉'.repeat(25)), false);
  assert.equal(validLoginPassword(''), false);
  assert.equal(validLoginPassword('   '), false);
});
