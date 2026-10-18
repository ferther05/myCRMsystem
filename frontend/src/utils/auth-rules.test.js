import test from 'node:test'
import assert from 'node:assert/strict'
import { safeRedirect, validNewPassword, validLoginPassword } from './auth-rules.js'

test('login redirects remain internal and never loop into auth screens', () => {
  for (const invalid of [null, [], 'https://example.com', '//example.com', '/\\example.com',
    '/%2fexample.com', '/login', '/change-password?redirect=/login', '/workbench\n']) {
    assert.equal(safeRedirect(invalid), '/workbench')
  }
  assert.equal(safeRedirect('/customers?keyword=中文'), '/customers?keyword=中文')
  assert.equal(safeRedirect('/account'), '/account')
})

test('new password matches backend boundaries and character rules', () => {
  assert.equal(validNewPassword('Ab123456'), true)
  assert.equal(validNewPassword('A' + '1'.repeat(63)), true)
  for (const invalid of ['A123456', 'A' + '1'.repeat(64), 'abcdefgh', '12345678',
    'Password 123', '密码Password123', 'Password123\n']) assert.equal(validNewPassword(invalid), false)
})

test('login does not allow BCrypt truncation for multibyte input', () => {
  assert.equal(validLoginPassword('A'.repeat(72)), true)
  assert.equal(validLoginPassword('汉'.repeat(24)), true)
  assert.equal(validLoginPassword('汉'.repeat(25)), false)
  assert.equal(validLoginPassword(''), false)
  assert.equal(validLoginPassword('   '), false)
})
