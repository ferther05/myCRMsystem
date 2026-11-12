<!-- 文件作用：三角色工作台，共享授权查询，按工作重点组织入口、指标与业务事项。 -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElDialog, ElTabs, ElTabPane } from 'element-plus';
import PageHeading from '@/components/PageHeading.vue';
import AppIcon from '@/components/AppIcon.vue';
import WorkItemList from '@/components/WorkItemList.vue';
import { useAuthStore } from '@/store/auth';
import { rolePresentation } from '@/utils/page-presentation';
import { workspaces } from '@/utils/role-workspaces';
import * as api from '@/api/workbench';

const auth = useAuthStore(), router = useRouter();
const identity = computed(() => rolePresentation[auth.user?.roleCode]);
const workspace = computed(() => workspaces[auth.user?.roleCode]);
const data = ref(null), loading = ref(false), failure = ref('');
const category = ref(''), rows = ref([]), total = ref(0), rowLoading = ref(false), rowFailure = ref('');
const attentionCategory = ref('STAGNANT'), attention = ref([]), attentionFailure = ref(''), attentionLoading = ref(false);
const moreOpen = ref(false), moreCategory = ref(''), morePage = ref(1), moreRows = ref([]), moreTotal = ref(0), moreLoading = ref(false), moreFailure = ref('');
let generation = 0, rowGeneration = 0, attentionGeneration = 0, moreGeneration = 0, lastRefresh = 0;
const titles = computed(() => Object.fromEntries((data.value?.categories || []).map(item => [item.key, item])));
const mainCategories = computed(() => (workspace.value?.categories || []).map(key => titles.value[key]).filter(Boolean));
const stamp = computed(() => data.value?.generatedAt?.replace('T', ' ').slice(0, 19));
/** 金额显示两位小数，计数显示整数。 */
const metricValue = item => Number(item.value || 0).toLocaleString('zh-CN', { maximumFractionDigits: item.unit === '元' ? 2 : 0, minimumFractionDigits: item.unit === '元' ? 2 : 0 });
/** 获取汇总；角色切换或卸载后旧请求不能覆盖当前界面。 */
async function refresh() {
  const current = ++generation;
  lastRefresh = Date.now(); loading.value = true; failure.value = ''; data.value = null;
  try { const result = await api.overview(); if (current === generation) data.value = result; }
  catch (e) { if (current === generation) failure.value = e.message; }
  finally { if (current === generation) loading.value = false; }
  if (current === generation) await Promise.all([loadRows(), workspace.value?.attention ? loadAttention() : Promise.resolve()]);
}
/** 默认仅预览五项，完整授权总数来自服务端。 */
async function loadRows() {
  const current = ++rowGeneration; rowLoading.value = true; rowFailure.value = ''; rows.value = []; total.value = 0;
  try { const result = await api.items({ category: category.value, page: 1, size: 10 }); if (current === rowGeneration) { rows.value = result.list.slice(0, 5); total.value = result.total; } }
  catch (e) { if (current === rowGeneration) rowFailure.value = e.message; }
  finally { if (current === rowGeneration) rowLoading.value = false; }
}
/** 经理关注项单独加载，局部失败不阻断核验列表。 */
async function loadAttention() {
  const current = ++attentionGeneration; attention.value = []; attentionFailure.value = ''; attentionLoading.value = true;
  try { const result = await api.items({ category: attentionCategory.value, page: 1, size: 10 }); if (current === attentionGeneration) attention.value = result.list.slice(0, 3); }
  catch (e) { if (current === attentionGeneration) attentionFailure.value = e.message; }
  finally { if (current === attentionGeneration) attentionLoading.value = false; }
}
/** 更多窗口固定十条分页，翻页不回到第一页。 */
async function loadMore() {
  const current = ++moreGeneration; moreLoading.value = true; moreFailure.value = ''; moreRows.value = [];
  try { const result = await api.items({ category: moreCategory.value, page: morePage.value, size: 10 }); if (current === moreGeneration) { moreRows.value = result.list; moreTotal.value = result.total; } }
  catch (e) { if (current === moreGeneration) { moreFailure.value = e.message; moreTotal.value = 0; } }
  finally { if (current === moreGeneration) moreLoading.value = false; }
}
/** 打开对应分类的完整十条分页清单。 */
function showMore(key) { moreCategory.value = key; morePage.value = 1; moreTotal.value = 0; moreOpen.value = true; void loadMore(); }
/** 工作项只导航至原业务，不提供绕过核验的本地完成按钮。 */
function openItem(item) { if (item.target?.startsWith('/') && !item.target.startsWith('//')) void router.push(item.target); }
/** 常用入口定位原页面或当前核验队列。 */
function enter(entry) { if (entry.category) { if (category.value === entry.category) void loadRows(); else category.value = entry.category; document.getElementById('work-items')?.scrollIntoView({ block: 'nearest', behavior: 'instant' }); } else void router.push(entry.path); }
/** 回到首页或业务提交后重查状态，保持已读未办事项仍可见。 */
function refreshIfVisible() { if (!document.hidden && Date.now() - lastRefresh > 1500) void refresh(); }
watch(category, (value, old) => { if (old && value !== old) void loadRows(); });
watch(attentionCategory, () => { if (workspace.value?.attention) void loadAttention(); });
watch(() => [auth.user?.id, auth.user?.roleCode, auth.user?.teamId, auth.user?.version], () => {
  ++generation; ++rowGeneration; ++attentionGeneration; ++moreGeneration;
  data.value = null; rows.value = []; attention.value = []; moreOpen.value = false;
  category.value = workspace.value?.defaultCategory || ''; if (category.value) void refresh();
}, { immediate: true });
onMounted(() => { window.addEventListener('focus', refreshIfVisible); window.addEventListener('crm:business-updated', refreshIfVisible); });
onBeforeUnmount(() => { ++generation; ++rowGeneration; ++attentionGeneration; ++moreGeneration; window.removeEventListener('focus', refreshIfVisible); window.removeEventListener('crm:business-updated', refreshIfVisible); });
</script>
<template>
  <div class="workbench-page">
    <PageHeading :title="`${identity?.title || '我的'}工作台`" :description="`${auth.user?.realName || ''} · ${data?.scope || identity?.scope || ''}`"><el-button :loading="loading" @click="refresh">刷新</el-button></PageHeading>
    <section class="panel work-overview" aria-label="工作概览">
      <header class="overview-heading"><h2>{{ workspace?.focus }}</h2><small v-if="stamp">更新于 {{ stamp }}</small></header>
      <div v-loading="loading" class="metric-grid" aria-label="当前工作摘要"><button v-for="item in data?.metrics || []" :key="item.key" class="metric" :title="item.note" @click="item.category ? showMore(item.category) : router.push('/analytics')"><span>{{ item.title }}</span><div><strong>{{ metricValue(item) }}</strong><small>{{ item.unit }}</small></div></button></div>
      <nav class="quick-entries" aria-label="本角色常用工作"><el-button v-for="entry in workspace?.entries" :key="entry.title" :title="entry.description" @click="enter(entry)"><AppIcon :name="entry.icon" />{{ entry.title }}</el-button></nav>
      <el-alert v-if="failure" :title="failure" type="error" show-icon :closable="false" /><p v-if="failure" class="muted">汇总暂不可用，仍可使用上方入口。<el-button link type="primary" @click="refresh">重试</el-button></p>
    </section>
    <div class="work-columns" :class="{ paired: workspace?.attention }">
    <section id="work-items" class="panel work-section" aria-label="主要工作事项"><header><h2>{{ auth.user?.roleCode === 'SPECIALIST' ? '今天的工作' : auth.user?.roleCode === 'MANAGER' ? '待我处理' : '需要关注' }}</h2><el-button link type="primary" @click="showMore(category)">查看全部 {{ total }} 项 →</el-button></header>
      <el-tabs v-model="category"><el-tab-pane v-for="item in mainCategories" :key="item.key" :name="item.key" :label="`${item.title} ${item.count}`" /></el-tabs>
      <WorkItemList :rows="rows" :loading="rowLoading" :failure="rowFailure" @open="openItem" @retry="loadRows" />
      <p class="muted list-note">预览5项，点击事项进入详情处理。</p>
    </section>
    <section v-if="workspace?.attention" class="panel work-section" aria-label="团队需关注"><header><h2>团队需关注</h2><el-button link type="primary" @click="showMore(attentionCategory)">完整清单 →</el-button></header><el-tabs v-model="attentionCategory"><el-tab-pane name="STAGNANT" label="停滞商机" /><el-tab-pane name="OVERDUE" label="逾期回款" /></el-tabs><WorkItemList :rows="attention" :loading="attentionLoading" :failure="attentionFailure" @open="openItem" @retry="loadAttention" /></section>
    </div>
    <el-dialog v-model="moreOpen" :title="titles[moreCategory]?.title || '完整工作清单'" width="850px" class="work-items-dialog" @closed="++moreGeneration"><WorkItemList :rows="moreRows" :loading="moreLoading" :failure="moreFailure" @open="openItem" @retry="loadMore" /><el-pagination v-model:current-page="morePage" :page-size="10" :total="moreTotal" layout="total, prev, pager, next" @current-change="loadMore" /></el-dialog>
  </div>
</template>
<style scoped>
.workbench-page { max-width:1200px; margin:0 auto; }
.work-overview { padding:18px 22px; margin-bottom:18px; }
.overview-heading { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.overview-heading h2 { font-size:15px; margin:0; }
.overview-heading>small { color:var(--crm-text-secondary); font-size:11px; }
.metric-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:0; margin:18px 0; min-height:20px; }
.metric { text-align:left; padding:0 18px; color:var(--crm-text-main); font:inherit; cursor:pointer; background:transparent; border:0; border-left:1px solid var(--crm-border); min-width:0; }
.metric:first-child { border-left:0; padding-left:0; }
.metric>span { font-size:13px; color:var(--crm-text-secondary); }
.metric strong { font-size:clamp(21px,2vw,28px); font-weight:600; overflow-wrap:anywhere; }
.metric div { margin:9px 0 0; }.metric small { margin-left:7px; }
.quick-entries { display:flex; gap:10px; flex-wrap:wrap; border-top:1px solid var(--crm-border); padding-top:14px; }
.quick-entries .el-button { margin:0; }.quick-entries .el-icon { margin-right:7px; }
.work-columns.paired { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:18px; align-items:start; }
.work-section { padding:18px 22px; margin-bottom:18px; min-width:0; }
.work-section header { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.work-section h2 { font-size:16px; margin:0 0 8px; }.muted { color:var(--crm-text-secondary); font-size:12px; line-height:1.7; }.list-note { margin:10px 0 0; }.el-pagination { margin-top:20px; max-width:100%; overflow:auto; }
button:focus-visible { outline:2px solid var(--role-accent); outline-offset:3px; }.metric:hover { color:var(--role-accent); }
@media(max-width:1000px){.work-columns.paired{grid-template-columns:1fr;gap:0;}}
@media(max-width:600px){.work-overview,.work-section{padding:14px;}.overview-heading{align-items:flex-start;flex-direction:column;gap:6px;}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 0;}.metric:nth-child(odd){border-left:0;padding-left:0;}.work-section header{flex-wrap:wrap;gap:0;}}
</style>
<style>.work-items-dialog{max-width:calc(100vw - 24px);}</style>
