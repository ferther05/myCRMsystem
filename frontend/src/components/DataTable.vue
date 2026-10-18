<script setup>
defineProps({ rows: { type: Array, default: () => [] }, total: { type: Number, default: 0 }, loading: Boolean, error: String, page: { type: Number, default: 1 }, size: { type: Number, default: 20 } })
const emit = defineEmits(['update:page', 'update:size', 'retry'])
function changeSize(value) { emit('update:page', 1); emit('update:size', value) }
</script>
<template>
  <div class="panel data-table">
    <div v-if="error" class="table-error" role="alert"><p>{{ error }}</p><el-button @click="emit('retry')">重新加载</el-button></div>
    <template v-else>
      <el-table v-loading="loading" :data="rows" empty-text="暂无记录" style="width: 100%"><slot /></el-table>
      <div class="pagination"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page" :page-size="size" :page-sizes="[10, 20, 50, 100]" @update:current-page="emit('update:page', $event)" @update:page-size="changeSize" /></div>
    </template>
  </div>
</template>
<style scoped>.data-table { overflow: hidden; }.pagination { padding: 18px; overflow-x: auto; }.table-error { padding: 32px; text-align: center; }</style>
