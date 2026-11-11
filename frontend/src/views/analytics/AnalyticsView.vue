<!-- 文件作用：三角色共用销售看板，区分当前存量与期间业绩。 -->
<script setup>
import { useAutoQuery } from '@/composables/useAutoQuery';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElSelect, ElOption, ElEmpty, ElCollapse, ElCollapseItem } from 'element-plus';
import PageHeading from '@/components/PageHeading.vue';
import BaseChart from '@/components/BaseChart.vue';
import PagedItems from '@/components/PagedItems.vue';
import * as api from '@/api/analytics';
const router = useRouter();
const filters = reactive({ period: 'month', from: '', to: '', ownerId: null });
const data = ref(null), owners = ref([]), loading = ref(false), failure = ref(''), appliedOwner = ref('全部负责人');
let generation = 0;
/** 展示金额，精确汇总由后端DECIMAL完成。 */
const money = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** 使用本地日历，不通过UTC转换日期。 */
const date = (v) => `${v.getFullYear()}-${String(v.getMonth() + 1).padStart(2, '0')}-${String(v.getDate()).padStart(2, '0')}`;
/** 设置日期快捷项，自定义时保留输入。 */
function periodChanged() {
  if (filters.period === 'custom') return;
  const now = new Date(), from = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (filters.period === 'week') from.setDate(from.getDate() - ((from.getDay() + 6) % 7));
  if (filters.period === 'month') from.setDate(1);
  if (filters.period === 'quarter') { from.setDate(1); from.setMonth(Math.floor(now.getMonth() / 3) * 3); }
  if (filters.period === 'year') { from.setDate(1); from.setMonth(0); }
  filters.from = date(from); filters.to = date(now);
}
/** 新查询清空旧范围结果，只接收最新响应。 */
async function load(refresh = false) {
  const current = ++generation;
  if (!filters.from || !filters.to || filters.from > filters.to) { failure.value = '请选择正确的开始和结束日期'; data.value = null; loading.value = false; return; }
  loading.value = true; failure.value = ''; data.value = null;
  try {
    const result = await api.overview({ from: filters.from, to: filters.to, ownerId: filters.ownerId || undefined, refresh });
    if (current !== generation) return;
    data.value = result; appliedOwner.value = owners.value.find((x) => x.id === filters.ownerId)?.name || '全部负责人';
  } catch (e) { if (current === generation) failure.value = e.message; }
  finally { if (current === generation) loading.value = false; }
}
periodChanged();
useAutoQuery({ filters: () => ({ ...filters }), load,
  invalidate: () => { ++generation; data.value = null; loading.value = true; },
});
const cards = computed(() => data.value ? [
  { title: '当前私海客户', value: data.value.metrics.customerCount, unit: '位', note: '当前归属范围，含全部创建时间' },
  { title: '当前在办商机', value: data.value.metrics.openOpportunities, unit: '个', note: '不含赢单、输单、关闭' },
  { title: '期间核验签约', value: money(data.value.metrics.signedAmount), unit: '元', note: '首次文件核验日期，含已终止历史' },
  { title: '期间实际到账', value: money(data.value.metrics.receivedAmount), unit: '元', note: '按到账日期，不含已作废记录' },
  { title: '当前未收金额', value: money(data.value.metrics.outstandingAmount), unit: '元', note: '已签合同当前余额，含历史合同' },
  { title: '当前逾期金额', value: money(data.value.metrics.overdueAmount), unit: '元', note: '当前计划已到期未收，今日到期不算逾期' },
] : []);
const funnel = computed(() => ({ tooltip: { trigger: 'item', valueFormatter: (v) => `${v} 个` }, series: [{ type: 'funnel', left: '22%', width: '56%', top: 12, bottom: 20, sort: 'none', gap: 5, label: { position: 'inside', formatter: '{b} {c}' }, data: (data.value?.stages || []).filter((x) => !['LOST', 'CLOSED'].includes(x.code) && x.count > 0).map((x) => ({ name: x.name, value: x.count, amount: x.amount })) }] }));
const trend = computed(() => ({ color: ['#2563eb', '#0d9488'], tooltip: { trigger: 'axis' }, legend: { top: 0 }, grid: { left: 18, right: 24, top: 45, bottom: 12, containLabel: true }, xAxis: { type: 'category', data: (data.value?.trends || []).map((x) => x.month) }, yAxis: { type: 'value', name: '元' }, series: [{ name: '核验签约', type: 'bar', barMaxWidth: 30, data: (data.value?.trends || []).map((x) => Number(x.signedAmount)) }, { name: '实际到账', type: 'line', symbolSize: 7, data: (data.value?.trends || []).map((x) => Number(x.receivedAmount)) }] }));
const rankingChart = computed(() => {
  const rows = (data.value?.ranking || []).slice(0, 10).reverse();
  return { color: ['#2563eb', '#0d9488'], tooltip: { trigger: 'axis' }, legend: { top: 0 }, grid: { left: 12, right: 20, top: 38, bottom: 12, containLabel: true }, xAxis: { type: 'value', name: '元' }, yAxis: { type: 'category', data: rows.map((r) => r.ownerName) }, series: [{ name: '核验签约', type: 'bar', barMaxWidth: 18, data: rows.map((r) => Number(r.signedAmount)) }, { name: '实际到账', type: 'bar', barMaxWidth: 18, data: rows.map((r) => Number(r.receivedAmount)) }] };
});
onMounted(async () => { try { owners.value = await api.owners(); } catch (e) { failure.value = e.message; return; } await load(); });
</script>
<template>
  <div class="analytics">
    <PageHeading title="数据看板与业绩统计" description="按本人、团队或全局权限展示；当前业务和期间业绩分开统计。" />
    <form class="panel filters" @submit.prevent="load(false)">
      <el-select v-model="filters.period" aria-label="统计时间" @change="periodChanged"><el-option label="今天" value="today" /><el-option label="本周" value="week" /><el-option label="本月" value="month" /><el-option label="本季度" value="quarter" /><el-option label="本年" value="year" /><el-option label="自定义" value="custom" /></el-select>
      <label>开始日期<input v-model="filters.from" type="date" :max="date(new Date())" required @change="filters.period = 'custom'" /></label><label>结束日期<input v-model="filters.to" type="date" :max="date(new Date())" required @change="filters.period = 'custom'" /></label>
      <el-select v-model="filters.ownerId" aria-label="负责人" placeholder="全部负责人" clearable filterable><el-option v-for="owner in owners" :key="owner.id" :value="owner.id" :label="`${owner.name} · ${owner.teamName}`" /></el-select>
      <span class="auto-query-note">自动筛选</span><el-button :disabled="loading" @click="load(true)">刷新最新数据</el-button>
    </form>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon /><el-button v-if="failure" @click="load(true)">重试</el-button>
    <div v-loading="loading" class="dashboard-content"><template v-if="data">
      <p class="range-note">{{ data.scope }} · {{ appliedOwner }} · 期间 {{ data.from }} 至 {{ data.to }} · 统计生成 {{ data.generatedAt.replace('T', ' ').slice(0, 19) }} · {{ data.source }}</p>
      <div class="metric-grid"><section v-for="card in cards" :key="card.title" class="panel metric"><span>{{ card.title }}</span><div><strong>{{ card.value }}</strong><small>{{ card.unit }}</small></div><p>{{ card.note }}</p></section></div>
      <el-alert v-if="data.legacyContracts || data.legacyReceipts" type="info" :closable="false" :title="`所选期间含存量补录：${data.legacyContracts}份合同、${data.legacyReceipts}笔到账。历史归属沿用原业务记录，无法保证还原全部旧交接。`" />
      <div class="chart-grid"><section class="panel section"><h2>当前阶段分布</h2><p class="muted">所选期间创建商机的当前阶段；不是历史转化率。</p><div class="funnel"><BaseChart v-if="data.stages.some(x => !['LOST', 'CLOSED'].includes(x.code) &amp;&amp; x.count > 0)" :option="funnel" label="当前商机阶段漏斗" /><el-empty v-else description="所选创建期间暂无漏斗商机" /></div><div class="stage-list"><span v-for="stage in data.stages" :key="stage.code">{{ stage.name }} <b>{{ stage.count }}</b></span></div></section><section class="panel section"><h2>签约与到账趋势</h2><p class="muted">按月汇总所选日期内发生的核验签约和实际到账，缺失月份补零。</p><BaseChart :option="trend" label="签约和实际回款月度趋势" /></section></div>
      <section class="panel section"><h2>当前签约合同回款概况</h2><p class="muted">同一组当前归属、已签或履约/结清合同的累计口径，不受日期筛选影响；不含已终止合同。</p><div class="conversion-values"><span>合同总额 <b>{{ money(data.metrics.currentContractAmount) }}元</b></span><span>累计已收 <b>{{ money(data.metrics.currentReceivedAmount) }}元</b></span><span>累计回款率 <b>{{ data.metrics.currentCollectionRate == null ? '—' : `${data.metrics.currentCollectionRate}%` }}</b></span></div></section>
      <section class="panel section"><h2>历史转化与签约周期</h2><p class="muted">所选期间创建的同一批商机，截至结束日期的阶段历史；旧数据缺少历史时不猜测。</p><div class="conversion-values"><span>创建商机 <b>{{ data.conversion.cohortCount }}</b></span><span>到达成交 <b>{{ data.conversion.dealCount }}</b></span><span>成交后赢单 <b>{{ data.conversion.wonCount }}</b></span><span>成交→赢单 <b>{{ data.conversion.dealToWonRate == null ? '—' : `${data.conversion.dealToWonRate}%` }}</b></span><span>平均签约周期 <b>{{ data.conversion.averageSigningDays == null ? '—' : `${data.conversion.averageSigningDays}天` }}</b></span></div></section>
      <section v-if="data.processQuality" class="panel section process-section"><el-collapse><el-collapse-item title="过程质量分析" name="process-quality"><p class="muted">停留天数统计当前全部在办商机，不受日期筛选影响；回退次数统计所选期间。两者均受负责人和角色范围限制。</p><div class="process-grid"><div><h3>当前阶段平均停留</h3><table class="quality-table" aria-label="当前阶段平均停留"><thead><tr><th scope="col">阶段</th><th scope="col">商机数</th><th scope="col">平均停留（天）</th></tr></thead><tbody><tr v-for="row in data.processQuality.stageDurations" :key="row.code"><th scope="row">{{ row.name }}</th><td>{{ row.opportunityCount }}</td><td>{{ row.opportunityCount ? Number(row.averageDays).toFixed(1) : '—' }}</td></tr></tbody></table><p class="muted">按最近一次进入当前阶段起算；缺少对应阶段日志时从商机创建时间起算。</p></div><div class="rollback-metric"><span>期间阶段回退</span><strong>{{ data.processQuality.rollbackCount }}</strong><small>次 · 同一商机多次回退分别计数</small></div></div></el-collapse-item></el-collapse></section>
      <section class="panel section"><h2>业绩排行</h2><p class="muted">按核验签约金额、实际到账金额降序；按业务发生时的归属统计，不随客户交接搬移。图显示前10名，表格每页10条。</p><BaseChart v-if="data.ranking.length" :option="rankingChart" label="签约与回款业绩排行" /><el-empty v-else description="所选期间暂无业绩" /><PagedItems :items="data.ranking"><template #default="{ rows }"><el-table :data="rows" empty-text="暂无业绩"><el-table-column prop="ownerName" label="人员" min-width="140" /><el-table-column prop="teamName" label="业绩所属团队" min-width="160" /><el-table-column prop="signedCount" label="签约份数" width="100" /><el-table-column label="核验签约（元）" min-width="150" align="right"><template #default="{ row }">{{ money(row.signedAmount) }}</template></el-table-column><el-table-column label="实际到账（元）" min-width="150" align="right"><template #default="{ row }">{{ money(row.receivedAmount) }}</template></el-table-column><el-table-column prop="wonCount" label="首次赢单数" width="110" /></el-table></template></PagedItems></section>
      <section class="panel section"><h2>我的待处理事项</h2><p class="muted">实时读取本人待办，不受上面的统计期间或负责人筛选影响。进入后仍按业务权限核对。</p><div class="action-grid"><el-button v-for="action in data.actions" :key="action.type" @click="router.push(action.target)">{{ action.title }} · {{ action.count }}</el-button><span v-if="!data.actions.length" class="muted">暂无待处理事项</span></div></section>
      <section class="panel section"><el-collapse><el-collapse-item title="统计口径与常见问题" name="rules"><p>当前客户、在办商机、未收与逾期显示当前全部存量；日期仅限制期间业绩、月度趋势、排行及创建批次。核验签约按首次文件核验时间，签署日由人工填写，不替代核验事实；已终止合同保留历史签约事实。</p><p>签约与到账可能来自不同合同批次，不能将两者相除称为回款率。当前未收也不等于所选期间签约减到账。作废到账记录不计入回款；当前有效或暂停计划中的到期未收按今日计算逾期。</p><p>历史转化以阶段日志为依据，期间末仍未完成的商机会影响该批次转化率；没有成交历史时显示“—”，不是0%。历史业绩仅提供汇总，不赋予原负责人访问已交接客户明细的权限。</p><p>统计缓存最长60秒，业务写入提交后主动失效；点“刷新最新数据”可绕过缓存。Redis暂不可用时查询数据库。当前按单实例设计。</p></el-collapse-item></el-collapse></section>
      <div class="business-links"><span>业务列表（当前权限内全部记录）：</span><el-button link type="primary" @click="router.push('/customers')">客户管理</el-button><el-button link type="primary" @click="router.push('/opportunities')">商机管理</el-button><el-button link type="primary" @click="router.push({ path: '/opportunities', query: { view: 'contracts' } })">合同与回款</el-button></div>
    </template></div>
  </div>
</template>
<style scoped>
.filters { display:flex; align-items:center; gap:16px; flex-wrap:wrap; padding:20px; margin-bottom:18px; }.filters .el-select { width:150px; }.filters .el-select:nth-of-type(2) { width:220px; }.filters label { display:flex; gap:8px; align-items:center; font-size:13px; color:var(--crm-text-secondary); }.filters input { border:1px solid var(--crm-border); border-radius:4px; padding:7px; min-width:0; background:white; color:var(--crm-text-main); }
.range-note,.muted { color:var(--crm-text-secondary); font-size:13px; line-height:1.7; }.metric-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; margin-bottom:18px; }.metric { padding:20px; min-width:0; }.metric>span { font-size:13px; color:var(--crm-text-secondary); }.metric strong { font-size:clamp(21px,2vw,29px); font-weight:600; overflow-wrap:anywhere; }.metric small { margin-left:7px; color:var(--crm-text-secondary); }.metric p { font-size:12px; color:var(--crm-text-secondary); margin:8px 0 0; line-height:1.6; }
.chart-grid { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1.2fr); gap:18px; margin-top:18px; }.section { padding:20px; margin-bottom:18px; min-width:0; }.section h2 { margin:0 0 8px; font-size:15px; }.funnel { max-width:480px; margin:auto; }.stage-list,.conversion-values,.action-grid { display:flex; flex-wrap:wrap; gap:14px; font-size:13px; }.stage-list { justify-content:center; color:var(--crm-text-secondary); }.conversion-values b { margin-left:8px; font-size:17px; color:var(--crm-text-main); }.conversion-values { gap:20px; }.action-grid .el-button { margin:0; }.business-links { display:flex; flex-wrap:wrap; align-items:center; gap:12px; font-size:13px; }.dashboard-content { min-height:180px; }
.process-grid { display:grid; grid-template-columns:minmax(0,1.5fr) minmax(150px,.5fr); gap:20px; align-items:center; }.process-grid h3 { margin:8px 0 12px; font-size:14px; }.rollback-metric { display:flex; flex-direction:column; gap:6px; color:var(--crm-text-secondary); }.rollback-metric strong { color:var(--crm-text-main); font-size:28px; }.rollback-metric small { font-size:12px; }
.quality-table { width:100%; border-collapse:collapse; font-size:13px; table-layout:fixed; }.quality-table th,.quality-table td { border-bottom:1px solid var(--crm-border); padding:10px 6px; text-align:right; overflow-wrap:anywhere; }.quality-table th { font-weight:500; }.quality-table th:first-child { text-align:left; width:23%; }.quality-table th:last-child { width:45%; }
@media(max-width:1100px){.metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.chart-grid { grid-template-columns:1fr; }}
@media(max-width:600px){.filters { padding:14px; }.filters label { width:100%; justify-content:space-between; }.filters .el-select,.filters .el-select:nth-of-type(2) { width:100%; }.metric-grid { gap:10px; }.metric { padding:14px; }.section { padding:14px; }.conversion-values { flex-direction:column; gap:10px; }.range-note { overflow-wrap:anywhere; }}
@media(max-width:600px){.process-grid { grid-template-columns:1fr; }.rollback-metric { flex-direction:row; align-items:baseline; }}
</style>
