<!-- 文件作用：为近三天创建的业务记录显示轻量新增标识，每分钟更新到期状态。 -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { isRecentRecord } from '@/utils/recent-record';
defineProps({ createdAt: String });
const now = ref(Date.now()); let timer;
onMounted(() => { timer = setInterval(() => { now.value = Date.now(); }, 60000); });
onBeforeUnmount(() => clearInterval(timer));
</script>
<template><span v-if="isRecentRecord(createdAt, now)" class="recent-badge" title="最近72小时内创建">新增</span></template>
<style scoped>.recent-badge { display:inline-block; margin-left:7px; padding:1px 6px; border:1px solid #d7e7ef; border-radius:4px; background:#edf5f8; color:#38637a; font-size:11px; line-height:18px; font-weight:400; vertical-align:middle; white-space:nowrap; }</style>
