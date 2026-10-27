<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeading from '@/components/PageHeading.vue'
import BaseChart from '@/components/BaseChart.vue'
import { Search } from '@element-plus/icons-vue'
import { ElSelect, ElOption, ElEmpty } from 'element-plus'
import * as api from '@/api/opportunities'

const filters = reactive({ from: '', to: '', ownerId: null })
const owners = ref([])
const rows = ref([]), loading = ref(false), failure = ref('')
const names = { NEW: '线索', FOLLOWING: '跟进', QUOTED: '报价', DEAL: '成交', WON: '赢单', LOST: '输单', CLOSED: '关闭' }
const option = computed(() => ({
  tooltip: { trigger: 'item', formatter: (item) => `${item.name}<br/>商机数：${item.value}` },
  legend: { bottom: 0 },
  series: [{
    type: 'funnel', left: '22%', top: 20, bottom: 56, width: '56%', min: 0, sort: 'none', gap: 4,
    label: { show: true, position: 'inside', formatter: (item) => `${item.name} ${item.value}` },
    data: rows.value.filter(item => !['LOST', 'CLOSED'].includes(item.stageCode)).map(item => ({
      name: names[item.stageCode], value: item.opportunityCount,
      ...(['DEAL', 'WON'].includes(item.stageCode) ? { label: { position: 'right', color: '#475569', fontSize: 11 } } : {}),
    })),
  }],
  media: [{ query: { maxWidth: 600 }, option: { series: [{ left: '16%', width: '68%' }] } }],
}))
const totalAmount = computed(() => rows.value.reduce((sum, row) => sum + Number(row.expectedAmount || 0), 0))
async function load() { loading.value = true; failure.value = ''; try { rows.value = await api.getOpportunityFunnel({ from: filters.from || undefined, to: filters.to || undefined, ownerId: filters.ownerId || undefined }) } catch (error) { failure.value = error.message } finally { loading.value = false } }
onMounted(async () => { await load(); try { owners.value = await api.listOpportunityOwners() } catch (error) { failure.value = error.message } })
</script>
<template>
  <div>
    <PageHeading title="销售漏斗" eyebrow="ANALYTICS" />
    <form class="panel filters" @submit.prevent="load"><label>创建时间从 <input v-model="filters.from" type="date" /></label><label>至 <input v-model="filters.to" type="date" /></label><el-select v-model="filters.ownerId" placeholder="全部负责人" aria-label="负责人" clearable><el-option v-for="owner in owners" :key="owner.id" :value="owner.id" :label="owner.realName" /></el-select><el-button type="primary" :icon="Search" native-type="submit" :loading="loading">查询</el-button></form>
    <el-alert v-if="failure" :title="failure" type="error" show-icon :closable="false" class="failure" /><div class="summary-grid"><section class="panel summary"><span>商机总数</span><strong>{{ rows.reduce((sum, item) => sum + Number(item.opportunityCount || 0), 0) }}</strong></section><section class="panel summary"><span>预计合同总额</span><strong>¥ {{ totalAmount.toFixed(2) }}</strong></section></div>
    <section class="panel chart-panel" v-loading="loading"><div class="section-title"><h2>阶段漏斗</h2></div><div v-if="rows.length" class="funnel-chart"><BaseChart :option="option" label="销售阶段漏斗图" /></div><el-empty v-else description="暂无商机" /></section>
    <section class="panel table-panel"><el-table :data="rows" empty-text="暂无漏斗数据"><el-table-column label="阶段" min-width="120"><template #default="{ row }">{{ names[row.stageCode] || row.stageCode }}</template></el-table-column><el-table-column prop="opportunityCount" label="商机数" width="120" /><el-table-column label="预计金额（元）" min-width="180"><template #default="{ row }">¥ {{ Number(row.expectedAmount || 0).toFixed(2) }}</template></el-table-column></el-table></section>
  </div>
</template>
<style scoped>
.funnel-chart { width:100%;max-width:520px;margin:12px auto 0; }.funnel-chart :deep(.base-chart) { height:340px; }
@media(max-width:600px){.funnel-chart { max-width:360px; }.funnel-chart :deep(.base-chart){height:310px;}}
.filters .el-select { width:180px; }.summary strong { overflow-wrap:anywhere; }
.filters { display:flex;align-items:center;gap:14px;padding:18px;margin-bottom:18px;flex-wrap:wrap; }.filters label { color:var(--crm-text-secondary);font-size:13px;display:flex;align-items:center;gap:8px; }.filters input { border:1px solid var(--crm-border);border-radius:4px;padding:7px 9px;color:var(--crm-text-main);background:#fff; }.summary-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-bottom:18px; }.summary { padding:20px 22px; }.summary span { display:block;color:var(--crm-text-secondary);font-size:12px;margin-bottom:8px; }.summary strong { font-size:25px;font-weight:600;color:var(--crm-text-main); }.chart-panel,.table-panel { padding:20px;margin-bottom:18px; }.section-title { display:flex;justify-content:space-between;align-items:center;gap:12px; }.section-title h2 { font-size:15px;margin:0; }.section-title span { font-size:12px;color:var(--crm-text-secondary); }.failure { margin-bottom:18px; } @media(max-width:600px){.summary-grid{grid-template-columns:1fr;}.section-title{align-items:flex-start;flex-direction:column;}}
</style>
