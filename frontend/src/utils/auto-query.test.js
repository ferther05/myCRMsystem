/* 文件作用：验证自动筛选的防抖、首屏重置、分页、数组条件和卸载清理。 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createRenderer, reactive, ref, h } from 'vue';
import { useAutoQuery } from '../composables/useAutoQuery.js';

test('文字防抖只查询最终值，选择自动查，翻页不重置，卸载取消等待', t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const filters = reactive({ keyword: '', stage: '', tags: [] }), page = ref(2);
  const calls = []; let invalidated = 0;
  const renderer = createRenderer({
    createElement: () => ({}), insert() {}, remove() {}, setElementText() {},
    createText: () => ({}), setText() {}, createComment: () => ({}),
    parentNode() {}, nextSibling() {}, patchProp() {},
  });
  const app = renderer.createApp({ setup() {
    useAutoQuery({ filters: () => ({ ...filters }), page,
      load: () => calls.push({ keyword: filters.keyword, stage: filters.stage, page: page.value }),
      invalidate: () => invalidated++,
    }); return () => h('div');
  } });
  app.mount({});
  filters.keyword = '华'; t.mock.timers.tick(200); filters.keyword = '华东';
  assert.equal(page.value, 1, '输入变化就重置页码，不在防抖期间保留旧页码');
  assert.equal(calls.length, 0); assert.equal(invalidated, 2);
  t.mock.timers.tick(349); assert.equal(calls.length, 0);
  t.mock.timers.tick(1); assert.deepEqual(calls, [{ keyword: '华东', stage: '', page: 1 }]);
  page.value = 3; assert.equal(calls.at(-1).page, 3); assert.equal(calls.length, 2);
  filters.stage = 'QUOTED'; t.mock.timers.tick(0);
  assert.equal(calls.at(-1).page, 1); assert.equal(calls.length, 3);
  filters.tags.push('重点'); t.mock.timers.tick(0); assert.equal(calls.length, 4);
  filters.keyword = '等待取消'; app.unmount(); t.mock.timers.tick(400);
  assert.equal(calls.length, 4);
});
