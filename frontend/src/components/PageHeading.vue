<!-- 文件作用：统一页面标题、描述及右侧操作区域。 -->
<script setup>
defineProps({ title: String, description: String, eyebrow: String });
</script>
<template>
  <div class="page-heading">
    <div>
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-actions"><slot /></div>
  </div>
</template>
