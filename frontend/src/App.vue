<!-- 文件作用：应用根组件，承载当前路由页面。 -->
<script setup>
import { ElConfigProvider } from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
</script>
<template>
  <ElConfigProvider :locale="zhCn"><router-view /></ElConfigProvider>
</template>
