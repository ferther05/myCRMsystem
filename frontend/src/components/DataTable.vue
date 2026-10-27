<script setup>
defineProps({ rows: { type: Array, default: () => [] }, total: { type: Number, default: 0 }, loading: Boolean, error: String, page: { type: Number, default: 1 } })
const emit = defineEmits(['update:page', 'retry'])
</script>
<template>
  <div class="panel data-table">
    <div v-if="error" class="table-error" role="alert"><p>{{ error }}</p><el-button @click="emit('retry')">重新加载</el-button></div>
    <template v-else>
      <el-table v-loading="loading" :data="rows" empty-text="暂无记录" style="width: 100%"><slot /></el-table>
      <div class="pagination"><span>每页 10 条</span><el-pagination background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="10" :pager-count="5" @update:current-page="emit('update:page', $event)" /></div>
    </template>
  </div>
</template>
<style scoped>.data-table { overflow: hidden; }.pagination { padding: 18px; overflow-x: auto; display:flex;align-items:center;gap:14px;flex-wrap:wrap; }.pagination > span { font-size:13px;color:var(--crm-text-secondary); }.table-error { padding: 32px; text-align: center; }</style>
