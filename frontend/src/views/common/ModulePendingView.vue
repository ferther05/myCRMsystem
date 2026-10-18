<script setup>
import { useRoute, useRouter } from 'vue-router'
import { Clock, ArrowLeft } from '@element-plus/icons-vue'
import PageHeading from '@/components/PageHeading.vue'
const route = useRoute()
const router = useRouter()
</script>
<template><div><PageHeading :title="route.meta.title" :description="route.meta.description" /><section class="panel pending-panel"><div class="pending-icon"><el-icon><Clock /></el-icon></div><el-tag type="info" effect="plain">待开发</el-tag><h2>{{ route.meta.title }}尚未开放</h2><p>此工作区将在后续开发中逐步开放。<br />你可以先查看账号信息，或返回工作台。</p><el-button type="primary" :icon="ArrowLeft" @click="router.push('/workbench')">返回工作台</el-button></section></div></template>
<style scoped>.pending-panel { padding: 65px 24px; text-align: center; }.pending-icon { display: grid; place-items: center; width: 70px; height: 70px; border-radius: 18px; background: #eef3fb; color: #5275a2; font-size: 30px; margin: 0 auto 24px; }.pending-panel h2 { font-size: 22px; margin: 18px 0 12px; }.pending-panel p { color: var(--crm-text-secondary); line-height: 1.9; font-size: 13px; margin-bottom: 28px; }</style>
