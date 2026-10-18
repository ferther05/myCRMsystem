<script setup>
import { useRouter } from 'vue-router'
defineProps({ code: String })
const router = useRouter()
</script>
<template><section class="panel status-panel"><span class="status-code">{{ code }}</span><h1>{{ code === '403' ? '你暂时没有访问权限' : '这个页面不存在' }}</h1><p>{{ code === '403' ? '请返回工作台，或联系管理员确认账号权限。' : '页面地址可能有误，请返回工作台继续使用。' }}</p><el-button type="primary" @click="router.push('/workbench')">返回工作台</el-button></section></template>
<style scoped>.status-panel { text-align: center; padding: 75px 24px; }.status-code { display: block; color: #245ee5; font-size: 75px; line-height: 1.2; font-weight: 700; letter-spacing: 6px; margin-bottom: 25px; }.status-panel h1 { font-size: 23px; }.status-panel p { color: var(--crm-text-secondary); font-size: 13px; margin-bottom: 28px; }</style>
