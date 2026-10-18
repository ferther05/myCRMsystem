<script setup>
defineProps({ label: { type: String, required: true }, type: { type: String, default: 'info' } })
</script>
<template><el-tag :type="type" effect="light" round>{{ label }}</el-tag></template>
