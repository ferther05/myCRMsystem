<!-- 文件作用：根据角色组织侧栏菜单和当前页面高亮。 -->
<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { navigationFor } from '@/utils/role-workspaces';

import AppIcon from '@/components/AppIcon.vue';
import TaskNotificationBadge from '@/components/TaskNotificationBadge.vue';
import { useTaskNotificationsStore } from '@/store/task-notifications';
const props = defineProps({ compact: Boolean });
const emit = defineEmits(['navigate']);
const route = useRoute();
const auth = useAuthStore();
const notifications = useTaskNotificationsStore();
const groups = computed(() => navigationFor(auth.user?.roleCode));
const activePath = computed(() =>
  auth.user?.roleCode === 'ADMIN' && route.path === '/approvals' ? '/system' : route.path,
);
</script>
<template>
  <nav aria-label="主导航">
    <el-menu
      :default-active="activePath"
      :collapse="props.compact"
      :collapse-transition="false"
      router
      class="sidebar-menu"
      @select="emit('navigate')"
    >
      <template v-for="group in groups" :key="group.title">
        <div v-if="!compact" class="nav-group">{{ group.title }}</div>
        <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" :class="{ 'is-priority': item.primary }">
          <AppIcon :name="item.icon" />
          <TaskNotificationBadge v-if="item.path === '/tasks'" class="nav-task-badge" :unread-count="notifications.unreadCount" :pending-count="notifications.pendingCount" />
          <template #title><span>{{ item.title }}</span><small v-if="item.primary && !compact" class="priority-label">常用</small><span v-if="!item.ready" class="pending-dot" aria-label="待开发" /></template>
        </el-menu-item>
      </template>
    </el-menu>
  </nav>
</template>
<style scoped>
.sidebar-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #c0cde0;
  --el-menu-hover-bg-color: #ffffff14;
  --el-menu-active-color: #fff;
  border: 0;
  width: 100%;
}
.priority-label { margin-left:8px; font-size:10px; color:inherit; opacity:.8; }
.is-priority { color:#fff; font-weight:600; background:#ffffff09; }
.nav-group {
  font-size: 10px;
  color: #9aacc6;
  padding: 21px 23px 8px;
  letter-spacing: 1.5px;
}
.nav-group:first-child {
  padding-top: 10px;
}
.el-menu-item {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin: 3px 11px;
  border-radius: 6px;
  padding: 0 15px !important;
  font-size: 13px;
}
.nav-task-badge { position:absolute; right:12px; top:11px; }
.nav-task-badge.is-dot { top:18px; }
.el-menu--collapse .nav-task-badge { right:3px; top:3px; }
.el-menu--collapse .nav-task-badge.is-dot { right:6px; top:6px; }
.el-menu-item.is-active {
  background: var(--role-soft);
  color: var(--role-accent);
  box-shadow: inset 3px 0 var(--role-accent);
  font-weight:600;
}
.el-menu-item .el-icon {
  font-size: 18px;
  margin-right: 11px;
}
.pending-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #9eb0c9;
  margin-left: auto;
}
.is-active .pending-dot {
  background: white;
}
:deep(.el-menu-tooltip__trigger) {
  padding: 0 15px !important;
}
</style>
