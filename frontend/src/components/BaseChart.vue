<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, PieChart, FunnelChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, TitleComponent, AriaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, PieChart, FunnelChart, GridComponent, LegendComponent, TooltipComponent, TitleComponent, AriaComponent, CanvasRenderer])
const props = defineProps({ option: { type: Object, required: true }, label: { type: String, required: true } })
const container = ref()
let chart
let resizeObserver
function render() { chart?.setOption({ ...props.option, aria: { enabled: true } }, true) }
onMounted(() => {
  chart = init(container.value)
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(container.value)
})
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose(); chart = null })
</script>
<template><div ref="container" class="base-chart" role="img" :aria-label="label" /></template>
<style scoped>.base-chart { width: 100%; height: 320px; }</style>
