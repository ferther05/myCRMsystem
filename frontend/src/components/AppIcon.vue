<!-- 文件作用：按名称展示导航使用的图标。 -->
<script setup>
import {
  House,
  User,
  Connection,
  TrendCharts,
  Document,
  List,
  CircleCheck,
  OfficeBuilding,
  DataAnalysis,
  Setting,
} from '@element-plus/icons-vue';
defineProps({ name: String });
const icons = {
  House,
  User,
  Connection,
  TrendCharts,
  Document,
  List,
  CircleCheck,
  OfficeBuilding,
  DataAnalysis,
  Setting,
};
</script>
<template>
  <el-icon><component :is="icons[name] || Document" /></el-icon>
</template>
