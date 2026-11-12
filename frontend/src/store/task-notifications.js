/* 文件作用：共享当前账号的导航待办计数，隔离旧会话和过期响应。 */
import { ref } from 'vue';
import { defineStore } from 'pinia';
import { taskSummary } from '@/api/tasks';
import { applicationSummary } from '@/api/users';
import { useAuthStore } from '@/store/auth';
import { readToken } from '@/utils/session';

export const useTaskNotificationsStore = defineStore('task-notifications', () => {
  const unreadCount = ref(0), pendingCount = ref(0), accountPendingCount = ref(0);
  let generation = 0;

  /** 换号、退出或离开主布局时立即移除旧账号的提示。 */
  function clear() {
    ++generation;
    unreadCount.value = 0;
    pendingCount.value = 0;
    accountPendingCount.value = 0;
  }

  /** 每次响应只允许更新发起该请求的同一会话，晚到的请求不覆盖新计数。 */
  async function refresh() {
    const token = readToken(), current = ++generation;
    if (!token) { clear(); return; }
    const admin = useAuthStore().user?.roleCode === 'ADMIN';
    await Promise.allSettled([
      (async () => { try {
      const result = await taskSummary();
      if (current !== generation || readToken() !== token) return;
      pendingCount.value = Math.max(0, Number(result.pendingCount) || 0);
      unreadCount.value = Math.min(pendingCount.value, Math.max(0, Number(result.unreadCount) || 0));
    } catch {
      // 短暂网络故障保留本会话最后一次成功计数，下一次刷新重试。
    } })(),
      (async () => {
        if (!admin) { if (current === generation) accountPendingCount.value = 0; return; }
        try {
          const result = await applicationSummary();
          if (current === generation && readToken() === token)
            accountPendingCount.value = Math.max(0, Number(result.pendingCount) || 0);
        } catch { /* 失败保留当前会话上次成功结果，下次重试。 */ }
      })(),
    ]);
  }
  return { unreadCount, pendingCount, accountPendingCount, clear, refresh };
});
