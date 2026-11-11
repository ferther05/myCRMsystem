<!-- 文件作用：管理员查看自动回收状态，必要时应急暂停或检查执行结果。 -->
<script setup>
import DetailSection from '@/components/DetailSection.vue';
import { onMounted, ref, watch } from 'vue';
import { ElDescriptions, ElDescriptionsItem, ElCollapse, ElCollapseItem, ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import PageHeading from '@/components/PageHeading.vue';
import DataTable from '@/components/DataTable.vue';
import * as api from '@/api/recycle';
const router = useRouter();
const selectedRule = ref(null);
const rows = ref([]), total = ref(0), page = ref(1), keyword = ref(''), setting = ref(null), loading = ref(false), busy = ref(false), error = ref(''), maintenance = ref([]);
const decisions = { WAITING: '正常跟进', NEED_WARNING: '等待系统提醒', WARNING: '请及时跟进', ELIGIBLE: '等待系统回收', PROTECTED: '业务保护中' };
let generation = 0;
/** 列表时间统一显示到秒。 */
const time = (v) => v ? String(v).replace('T', ' ').slice(0, 19) : '—';
/** 预览不会更新预警或归属。 */
async function load() {
  const current = ++generation;
  loading.value = true; error.value = '';
  try { const [list, config] = await Promise.all([api.preview({ page: page.value, size: 10, keyword: keyword.value || undefined }), api.setting()]); if (current === generation) { rows.value = list.list; total.value = list.total; setting.value = config; } }
  catch (e) { if (current === generation) error.value = e.message; }
  finally { if (current === generation) loading.value = false; }
}
/** 搜索回到第一页。 */
function search() { if (page.value === 1) void load(); else page.value = 1; }
/** 开启前说明影响；关闭与扫描逐客户使用同一数据库配置锁。 */
async function toggle() {
  if (!setting.value || busy.value) return;
  try {
    const enabled = !setting.value.enabled;
    await ElMessageBox.confirm(enabled ? '恢复后系统每10分钟检查，自动预警并回收符合条件的客户，所有业务保护继续有效。' : '仅用于排查异常。暂停后不再自动预警或回收，现有记录保留；排查完成后请恢复运行。', enabled ? '恢复自动运行' : '应急暂停', { type: 'warning' });
    busy.value = true; await api.saveSetting({ enabled, version: setting.value.version }); await load();
  } catch (e) { if (!['cancel', 'close'].includes(e)) ElMessage.error(e.message); }
  finally { busy.value = false; }
}
/** 手动扫描保持同一开关约束，不提供绕过保护的强制回收。 */
async function scan() {
  try { await ElMessageBox.confirm(setting.value?.enabled ? '将处理当前实际业务，可能发送预警和回收客户。是否继续？' : '回收开关关闭，本次只检查并记录执行结果，不修改客户。', '执行当天扫描', { type: 'warning' }); busy.value = true; const r = await api.scan(); ElMessage[r.status === 'SUCCESS' ? 'success' : 'warning'](`扫描完成：检查成功${r.processed}项，变更${r.changed}项，失败${r.failedIds.length}项`); await load(); }
  catch (e) { if (!['cancel', 'close'].includes(e)) ElMessage.error(e.message); }
  finally { busy.value = false; }
}
watch(page, load); onMounted(load);
</script>
<template>
  <div>
    <PageHeading title="公海自动回收" description="系统自动提醒、到期检查和回收，日常无需人工执行；全部历史保留。" />
    <div class="panel recycle-toolbar">
      <el-alert v-if="setting" :title="setting.enabled ? '自动运行中 · 每10分钟检查，无需手动发送预警或执行扫描。' : '已应急暂停 · 系统暂不预警或回收，请在排查后恢复运行。'" :type="setting.enabled ? 'success' : 'warning'" :closable="false" />
      <div class="actions"><el-input v-model="keyword" placeholder="客户名称" clearable maxlength="160" @keyup.enter="search" /><el-button type="primary" @click="search">查询</el-button><el-button @click="load">刷新</el-button></div>
      <details class="secondary-disclosure"><summary>查看回收规则与保护条件</summary><p>首联48小时预警／72小时到期；有效跟进27天预警／30天到期。未结束商机、合同回款、待审跟进、审批与交接受保护。页面结果仅供预览，执行时会再次复核。</p></details>
      <el-collapse v-model="maintenance"><el-collapse-item title="异常处理（平时无需操作）" name="maintenance"><el-button :disabled="busy || !setting" @click="toggle">{{ setting?.enabled ? '应急暂停' : '恢复自动运行' }}</el-button><el-button :loading="busy" @click="scan">立即检查一次</el-button><el-button @click="router.push('/tasks')">查看执行记录</el-button></el-collapse-item></el-collapse>
    </div>
    <DataTable :rows="rows" :total="total" :loading="loading" :error="error" v-model:page="page" @retry="load">
      <el-table-column prop="customerName" label="客户" min-width="210" show-overflow-tooltip />
      <el-table-column label="负责人 / 团队" min-width="160"><template #default="{ row }">{{ row.ownerName }}<p class="muted">{{ row.teamName }}</p></template></el-table-column>
      <el-table-column label="判定" width="155"><template #default="{ row }"><el-tag :type="row.rule.decision === 'ELIGIBLE' ? 'warning' : 'info'">{{ decisions[row.rule.decision] }}</el-tag></template></el-table-column>
      <el-table-column label="回收截止" min-width="175"><template #default="{ row }"><p>{{ time(row.rule.deadlineAt) }}</p></template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="selectedRule = row">详情</el-button></template></el-table-column>
    </DataTable><el-drawer :model-value="Boolean(selectedRule)" @close="selectedRule = null" title="回收判定详情" size="min(620px, 100%)"><template v-if="selectedRule"><DetailSection title="当前判定" emphasis><h3>{{ selectedRule.customerName }}</h3><p>{{ decisions[selectedRule.rule.decision] }}</p><p>{{ selectedRule.rule.reason }}</p></DetailSection><DetailSection title="归属与时效"><el-descriptions :column="1" border><el-descriptions-item label="负责人 / 团队">{{ selectedRule.ownerName }} / {{ selectedRule.teamName }}</el-descriptions-item><el-descriptions-item label="预警时间">{{ time(selectedRule.rule.warningAt) }}</el-descriptions-item><el-descriptions-item label="回收截止">{{ time(selectedRule.rule.deadlineAt) }}</el-descriptions-item></el-descriptions></DetailSection><el-button type="primary" @click="router.push(`/customers?id=${selectedRule.id}`)">查看客户与延期记录</el-button></template></el-drawer>
  </div>
</template>
<style scoped>
.recycle-toolbar { padding: 18px; margin-bottom: 18px; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }
.actions .el-input { max-width: 240px; }
.recycle-toolbar > p, .muted { color: var(--crm-text-secondary); line-height: 1.7; font-size: 13px; }
</style>
