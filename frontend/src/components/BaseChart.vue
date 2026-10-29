<!-- 文件作用：封装 ECharts 渲染、容器缩放和销毁清理。 -->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { init, use } from 'echarts/core';
import { BarChart, LineChart, PieChart, FunnelChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
  AriaComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

use([
  BarChart,
  LineChart,
  PieChart,
  FunnelChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
  AriaComponent,
  CanvasRenderer,
]);
const props = defineProps({
  option: { type: Object, required: true },
  label: { type: String, required: true },
});
const container = ref();
let chart;
let resizeObserver;
/** 更新图表配置并启用无障碍说明。 */
function render() {
  chart?.setOption({ ...props.option, aria: { enabled: true } }, true);
}
onMounted(
  /* 挂载后创建图表并观察容器尺寸。 */ () => {
    chart = init(container.value);
    render();
    resizeObserver = new ResizeObserver(
      /* 容器尺寸变化时同步调整图表大小。 */ () => chart?.resize(),
    );
    resizeObserver.observe(container.value);
  },
);
watch(/* 读取需要监听的响应式字段。 */ () => props.option, render, { deep: true });
onBeforeUnmount(
  /* 卸载前断开尺寸监听并销毁图表。 */ () => {
    resizeObserver?.disconnect();
    chart?.dispose();
    chart = null;
  },
);
</script>
<template><div ref="container" class="base-chart" role="img" :aria-label="label" /></template>
<style scoped>
.base-chart {
  width: 100%;
  height: 320px;
}
</style>
