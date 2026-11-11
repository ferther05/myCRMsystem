<!-- 文件作用：未读显示红底白字数量；已读仍待办显示小红点；无待办隐藏。 -->
<script setup>
defineProps({ unreadCount: { type: Number, default: 0 }, pendingCount: { type: Number, default: 0 } });
</script>
<template>
  <span v-if="pendingCount > 0" class="task-notification-badge" :class="{ 'is-dot': unreadCount === 0 }"
    role="status" :aria-label="unreadCount > 0 ? `${unreadCount}条未读待处理提醒` : '还有待处理任务'">
    {{ unreadCount > 0 ? unreadCount : '' }}
  </span>
</template>
<style scoped>
.task-notification-badge { display:inline-flex; align-items:center; justify-content:center; flex-shrink:0; width:22px; height:22px; border-radius:50%; background:#df3547; color:#fff; font-size:11px; font-weight:600; line-height:1; font-variant-numeric:tabular-nums; }
.task-notification-badge.is-dot { width:8px; height:8px; }
</style>
