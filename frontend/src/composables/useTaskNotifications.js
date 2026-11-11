/* 文件作用：主布局统一刷新导航提醒，桌面与移动导航共享一次轮询。 */
import { onMounted, onBeforeUnmount, watch } from 'vue';
import { useAuthStore } from '@/store/auth';
import { useTaskNotificationsStore } from '@/store/task-notifications';

/** 登录、路由切换、业务提交、窗口重新聚焦以及可见时每30秒更新。 */
export function useTaskNotifications(route) {
  const auth = useAuthStore(), notifications = useTaskNotificationsStore();
  let timer, interval;
  const usable = () => Boolean(auth.token && auth.user && !auth.user.mustChangePassword);
  const cancel = () => { clearTimeout(timer); timer = undefined; };
  /** 合并短时间内的业务更新和导航事件，后台标签页暂停刷新。 */
  function schedule() {
    cancel();
    if (!usable() || document.hidden) return;
    timer = setTimeout(() => { timer = undefined; void notifications.refresh(); }, 200);
  }
  watch(() => [auth.token, auth.user?.id, auth.user?.mustChangePassword], () => {
    cancel(); notifications.clear();
    if (usable()) void notifications.refresh();
  }, { immediate: true, flush: 'sync' });
  watch(() => route.fullPath, schedule);
  onMounted(() => {
    interval = setInterval(schedule, 30000);
    window.addEventListener('crm:business-updated', schedule);
    window.addEventListener('focus', schedule);
    document.addEventListener('visibilitychange', schedule);
  });
  onBeforeUnmount(() => {
    cancel(); clearInterval(interval); notifications.clear();
    window.removeEventListener('crm:business-updated', schedule);
    window.removeEventListener('focus', schedule);
    document.removeEventListener('visibilitychange', schedule);
  });
}
