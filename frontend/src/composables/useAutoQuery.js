/* 文件作用：列表条件自动筛选，文字防抖、选择即查，统一取消过期请求。 */
import { onBeforeUnmount, watch } from 'vue';

/** 条件变化回到首屏；翻页保留条件；同一轮变更只发送最终查询。 */
export function useAutoQuery({ filters, page, size, load, invalidate, textKeys = ['keyword', 'region', 'action', 'bizId'] }) {
  let timer;
  let resetting = false;
  const cancel = () => { clearTimeout(timer); timer = undefined; };
  function run(reset = false) {
    cancel();
    if (reset && page) {
      resetting = true;
      page.value = 1;
      resetting = false;
    }
    return load();
  }
  watch(() => JSON.stringify(filters()), (value, previous) => {
    cancel();
    // 条件一变就回首屏；等待防抖时也不保留旧页码。invalidate 只清行数据，不清分页总数。
    if (page) { resetting = true; page.value = 1; resetting = false; }
    invalidate();
    const next = JSON.parse(value), old = JSON.parse(previous);
    const typing = textKeys.some(key => next[key] !== old[key]);
    timer = setTimeout(() => run(true), typing ? 350 : 0);
  }, { flush: 'sync' });
  if (page) watch(size ? [page, size] : page, () => {
    if (!resetting) { invalidate(); void run(); }
  }, { flush: 'sync' });
  onBeforeUnmount(() => { cancel(); invalidate(); });
  return { search: () => run(true), refresh: () => run() };
}
