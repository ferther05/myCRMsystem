<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ items: { type: Array, default: () => [] } })
const page = ref(1)
const rows = computed(() => props.items.slice((page.value - 1) * 10, page.value * 10))
watch(() => props.items, () => { page.value = 1 })
</script>
<template>
  <div class="paged-items">
    <slot :rows="rows" />
    <div v-if="items.length > 10" class="item-pagination"><span>每页 10 条</span><el-pagination v-model:current-page="page" :page-size="10" :total="items.length" :pager-count="5" layout="total, prev, pager, next" /></div>
  </div>
</template>
<style scoped>.item-pagination{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:16px 0;overflow-x:auto;}.item-pagination > span{font-size:13px;color:var(--crm-text-secondary);}</style>
