<!-- 文件作用：工作台复用事项清单，显式区分加载、失败与空态。 -->
<script setup>
import { ElEmpty } from 'element-plus';
defineProps({ rows: { type: Array, default: () => [] }, loading: Boolean, failure: String });
defineEmits(['open', 'retry']);
const time = value => value?.replace('T', ' ').slice(0, 19) || '未记录时间';
const money = value => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
</script>
<template>
  <div v-loading="loading" class="work-list" aria-live="polite" :aria-busy="loading">
    <div v-if="failure" role="alert" class="failure"><span>{{ failure }}</span><el-button @click="$emit('retry')">重新加载</el-button></div>
    <el-empty v-else-if="!loading && !rows.length" description="当前没有该类待处理事项" :image-size="65" />
    <ul v-else><li v-for="item in rows" :key="item.key"><div class="item-copy"><strong>{{ item.objectName }}</strong><p><span class="type-label">{{ item.title }}</span><span v-if="item.actorName">{{ item.actorName }}</span><time>{{ time(item.occurredAt) }}</time><span v-if="item.amount != null" class="amount">未收 {{ money(item.amount) }} 元</span></p></div><el-button link type="primary" @click="$emit('open', item)">去处理 →</el-button></li></ul>
  </div>
</template>
<style scoped>
.work-list { min-height:100px; }ul { list-style:none; padding:0; margin:0; }li { display:flex; align-items:center; gap:14px; padding:14px 0; border-bottom:1px solid var(--crm-border); }li:last-child { border-bottom:0; }.item-copy { flex:1; min-width:0; }strong { font-size:14px; font-weight:600; overflow-wrap:anywhere; }p { display:flex; gap:12px; flex-wrap:wrap; margin:7px 0 0; font-size:12px; color:var(--crm-text-secondary); }.type-label { color:var(--role-accent); }.amount { color:#a8461c; }.failure { display:flex; gap:14px; align-items:center; padding:18px 0; color:var(--crm-color-danger); font-size:13px; }
@media(max-width:600px){li { align-items:flex-start; gap:8px; }.el-button { margin-top:3px; }p { gap:5px 10px; }}
</style>
