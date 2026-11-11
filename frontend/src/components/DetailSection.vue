<!-- 文件作用：统一详情中的同类信息分区，重点区域使用轻量强调。 -->
<script setup>
defineProps({ title: String, description: String, emphasis: Boolean });
</script>
<template>
  <section class="detail-section" :class="{ 'is-emphasis': emphasis }">
    <header><h3>{{ title }}</h3><p v-if="description">{{ description }}</p></header>
    <slot />
  </section>
</template>
<style scoped>
.detail-section { border:1px solid var(--crm-border); border-radius:9px; padding:18px; margin:16px 0; background:white; min-width:0; }
.detail-section.is-emphasis { border-top:3px solid var(--crm-color-brand); background:#f8faff; }
header { margin-bottom:14px; }
h3 { font-size:14px; margin:0; color:var(--crm-text-main); }
header p { color:var(--crm-text-secondary); font-size:12px; margin:5px 0 0; }
@media(max-width:600px){.detail-section{padding:13px;}}
</style>
