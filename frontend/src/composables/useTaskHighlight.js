/* 文件作用：任务跳转后定位按钮，两次柔和提示相隔5秒，离开即取消。 */
import { nextTick, onBeforeUnmount, ref } from 'vue';

/** 容器内仅定位指定任务按钮；无权限时不伪造操作入口。 */
export function useTaskHighlight(root) {
  const target = ref('');
  let timers = [], epoch = 0;
  function clear() {
    epoch++;
    timers.forEach(clearTimeout);
    timers = [];
    root.value?.querySelectorAll('.task-flash').forEach(el => el.classList.remove('task-flash'));
    target.value = '';
  }
  async function highlight(key) {
    clear();
    const current = epoch;
    target.value = key;
    await nextTick();
    if (epoch !== current) return false;
    const button = root.value?.querySelector(`[data-task="${key}"]`);
    if (!button) return false;
    button.scrollIntoView({ block: 'nearest', behavior: 'auto' });
    button.focus({ preventScroll: true });
    const pulse = () => {
      if (epoch !== current || !button.isConnected) return;
      button.classList.remove('task-flash');
      void button.offsetWidth;
      button.classList.add('task-flash');
      timers.push(setTimeout(() => button.classList.remove('task-flash'), 1000));
    };
    pulse();
    timers.push(setTimeout(pulse, 5000));
    return true;
  }
  onBeforeUnmount(clear);
  return { highlight, clear, target };
}
