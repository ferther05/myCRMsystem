<script setup>defineProps({ title: String, description: String, eyebrow: String })</script>
<template>
  <div class="page-heading">
    <div><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span><h1>{{ title }}</h1><p v-if="description">{{ description }}</p></div>
    <div v-if="$slots.default" class="page-actions"><slot /></div>
  </div>
</template>
