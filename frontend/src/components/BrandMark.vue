<script setup>
defineProps({ light: Boolean, compact: Boolean })
</script>

<template>
  <div class="brand" :class="{ 'brand-light': light }">
    <img src="/favicon.svg" alt="" width="38" height="38" />
    <div v-if="!compact"><strong>CRM</strong><span>客户关系管理</span></div>
  </div>
</template>

<style scoped>
.brand { display: flex; align-items: center; gap: 12px; color: var(--crm-text-main); }
.brand img { flex-shrink: 0; }
.brand strong { display: block; font-size: 20px; letter-spacing: 1px; line-height: 1.2; }
.brand span { display: block; color: var(--crm-text-secondary); font-size: 11px; margin-top: 3px; letter-spacing: 1px; }
.brand-light { color: white; }
.brand-light span { color: #adbed7; }
</style>
