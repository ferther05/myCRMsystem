<!-- 文件作用：按角色展示三个常用入口、其他功能与默认收起的演示指引。 -->
<script setup>
import { computed, onMounted, ref } from 'vue';
import { ArrowRight, Refresh } from '@element-plus/icons-vue';
import PageHeading from '@/components/PageHeading.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useAuthStore } from '@/store/auth';
import { rolePresentation } from '@/utils/page-presentation';
import { modules, canVisit } from '@/router/modules';
import { getHealth } from '@/api/health';

const auth = useAuthStore();
const identity = computed(() => rolePresentation[auth.user?.roleCode]);
// 入口只定位已实现页面，不生成统计或替代业务审批。
const workspaces = {
  SPECIALIST: {
    focus: '先处理提醒，再跟进客户、推进商机',
    boundary: '办理本人及授权业务；阶段条件满足后由系统推进。',
    entries: [
      { path: '/tasks', title: '我的待办', icon: 'List', description: '查看到期跟进、客户预警与回款提醒，进入业务办理。' },
      { path: '/customers', title: '客户跟进', icon: 'User', description: '查联系人与往来记录，补充沟通结果和下一步安排。' },
      { path: '/opportunities', title: '商机与回款', icon: 'TrendCharts', description: '按当前阶段补齐跟进、报价、合作确认及签约回款。' },
    ],
    steps: [
      { path: '/tasks', title: '从提醒找到业务', detail: '选一条待办，点“去处理”；标记已读不会完成业务。' },
      { path: '/customers', title: '说明客户关系', detail: '展示联系人、沟通历史和下一步安排，解释这位客户由谁维护。' },
      { path: '/opportunities', title: '提交阶段材料', detail: '在商机详情查看当前阶段缺项，提交有效跟进；等待经理或管理员核验。' },
      { path: '/opportunities?view=contracts', title: '继续签约与回款', detail: '经理核验后继续登记合同、上传签署文件、提交回款计划和登记实际到账。' },
    ],
  },
  MANAGER: {
    focus: '先核验关键材料，再协调团队业务',
    boundary: '查看本团队，核验跟进及合同、审批回款计划；不手动切换商机阶段。',
    entries: [
      { path: '/tasks?type=CONTRACT_REVIEW', title: '合同核验待办', icon: 'CircleCheck', description: '定位待核对的登记表与最新签署文件，确认或退回补充。' },
      { path: '/opportunities', title: '团队商机', icon: 'TrendCharts', description: '核验本阶段跟进、检查卡点，按需协调协作与合同回款。' },
      { path: '/analytics', title: '团队业绩', icon: 'DataAnalysis', description: '先看应收与逾期，再查漏斗和必要的过程指标。' },
    ],
    steps: [
      { path: '/opportunities', title: '核验同一条商机', detail: '打开专员刚提交的商机，在“跟进与核验”确认或打回，查看系统重算后的阶段与缺项。' },
      { path: '/opportunities?view=contracts', title: '核对合同及回款计划', detail: '核对登记表、最新签署版本；按合同当前状态处理计划审批，退回时说明原因。' },
      { path: '/team', title: '说明团队分工', detail: '查看本团队成员；商机协作在该商机详情处理，不扩大客户档案权限。' },
      { path: '/analytics', title: '只讲一个团队问题', detail: '选定时间范围，说明一项逾期或阶段卡点；需要时再展开过程质量分析。' },
    ],
  },
  ADMIN: {
    focus: '先维护账号与归属，再检查系统运行',
    boundary: '维护全局账号、客户与商机分配；异常纠正保留原因和记录。',
    entries: [
      { path: '/system', title: '账号与团队配置', icon: 'Setting', description: '创建账号、配置角色团队，处理停用及密码重置。' },
      { path: '/customers', title: '客户录入与分配', icon: 'User', description: '录入客户并指定负责人，需要时办理同团队完整交接。' },
      { path: '/tasks?tab=jobs', title: '运行与审计', icon: 'List', description: '先检查任务执行结果，再切换操作记录追溯业务变化。' },
    ],
    steps: [
      { path: '/system', title: '交代角色与归属', detail: '展示三角色账号和团队范围；不以管理员代办所有销售操作。' },
      { path: '/customers', title: '展示资源分配', detail: '展示客户负责人、团队与公海规则；创建商机仍从商机管理办理。' },
      { path: '/tasks?tab=operations', title: '追溯刚才的业务', detail: '按业务对象查看操作人与字段变化，说明录入、核验及到账留下的记录。' },
      { path: '/tasks?tab=jobs', title: '检查自动机制', detail: '只查看扫描结果与失败项，必要时查看回收页面；演示不必执行扫描或批量交接。' },
    ],
  },
};
const workspace = computed(() => workspaces[auth.user?.roleCode]);
// 业务捷径复用菜单角色限制；未知角色不展示捷径。
const entries = computed(() => (workspace.value?.entries || []).filter((entry) => {
  const module = modules.find((item) => item.path === entry.path.split('?')[0]);
  return module && canVisit(module, auth.user?.roleCode);
}));
const otherSpaces = computed(() => modules.filter((item) => item.path !== '/workbench'
  && canVisit(item, auth.user?.roleCode)
  && !entries.value.some((entry) => entry.path === item.path)));
const health = ref(null);
const loading = ref(true);
const failure = ref('');

/** 刷新实际连接结果，失败时清空旧状态并允许重试。 */
async function checkHealth() {
  loading.value = true;
  failure.value = '';
  try {
    health.value = await getHealth();
  } catch (error) {
    health.value = null;
    failure.value = error.message;
  } finally {
    loading.value = false;
  }
}
onMounted(checkHealth);
</script>

<template>
  <div class="workbench-page">
    <PageHeading :title="`${identity?.title || '我的'}工作台`" :description="workspace?.focus || '查看当前工作入口'" />
    <section class="identity-strip panel" aria-label="当前工作重点">
      <span class="identity-icon"><AppIcon :name="identity?.icon || 'House'" /></span>
      <div class="identity-copy">
        <h2>{{ auth.user?.realName }}<span>{{ identity?.scope }}</span></h2>
        <p>{{ auth.user?.teamName || '系统管理工作空间' }} · {{ workspace?.boundary }}</p>
      </div>
      <router-link to="/tasks" class="all-todos">全部待办 <el-icon><ArrowRight /></el-icon></router-link>
    </section>

    <section aria-labelledby="priority-title">
      <h2 id="priority-title" class="section-title">常用工作入口</h2>
      <div class="priority-grid">
        <router-link v-for="(item, index) in entries" :key="item.path" :to="item.path" class="priority-card panel">
          <div class="card-top"><span class="entry-icon"><AppIcon :name="item.icon" /></span><span class="entry-order">{{ index === 0 ? '优先查看' : '常用操作' }}</span></div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
          <span class="entry-link">进入处理 <el-icon><ArrowRight /></el-icon></span>
        </router-link>
      </div>
    </section>

    <details class="secondary-panel panel">
      <summary>其他功能入口<span>按需展开</span></summary>
      <nav class="other-grid" aria-label="其他功能入口">
        <router-link v-for="item in otherSpaces" :key="item.path" :to="item.path"><AppIcon :name="item.icon" />{{ item.title }}<el-icon><ArrowRight /></el-icon></router-link>
      </nav>
    </details>

    <details v-if="workspace" class="demo-panel panel">
      <summary>本角色演示顺序<span>演示时展开</span></summary>
      <p class="demo-note">建议围绕同一客户、同一商机接力：专员说明业务 → 经理核验 → 专员补齐签约回款 → 管理员追溯。以下是操作指引，不代表业务已完成。</p>
      <ol class="demo-steps">
        <li v-for="step in workspace.steps" :key="step.title"><div><strong>{{ step.title }}</strong><p>{{ step.detail }}</p></div><router-link :to="step.path">去查看 <el-icon><ArrowRight /></el-icon></router-link></li>
      </ol>
    </details>

    <footer class="workbench-footer">
      <div class="connection" aria-live="polite">
        <span v-if="loading">正在检查连接…</span>
        <span v-else-if="health?.status === 'UP'">连接正常</span>
        <span v-else class="connection-error" role="alert">暂时无法连接：{{ failure || '请重新检查连接' }}</span>
        <el-button text :icon="Refresh" :loading="loading" aria-label="刷新连接状态" @click="checkHealth">{{ failure ? '重试' : '刷新' }}</el-button>
      </div>
      <div class="account-links"><router-link to="/account">个人中心</router-link><router-link to="/change-password">修改密码</router-link></div>
    </footer>
  </div>
</template>

<style scoped>
.workbench-page { max-width: 1200px; margin: 0 auto; }
.identity-strip { display: flex; align-items: center; gap: 16px; padding: 20px 24px; border-left: 4px solid var(--role-accent); }
.identity-icon, .entry-icon { display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 10px; background: var(--role-soft); color: var(--role-accent); font-size: 20px; flex-shrink: 0; }
.identity-copy { min-width: 0; flex: 1; }
.identity-copy h2 { margin: 0 0 6px; font-size: 17px; }
.identity-copy h2 span { display: inline-block; margin-left: 12px; color: var(--crm-text-secondary); font-size: 12px; font-weight: 400; }
.identity-copy p { margin: 0; color: var(--crm-text-secondary); font-size: 13px; line-height: 1.7; }
.all-todos, .entry-link, .demo-steps a { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; white-space: nowrap; }
.section-title { font-size: 15px; margin: 24px 0 12px; }
.priority-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.priority-card { display: flex; flex-direction: column; padding: 22px; color: var(--crm-text-main); transition: border-color .15s; }
.priority-card:hover { border-color: var(--role-accent); }
.card-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.entry-order { color: var(--crm-text-secondary); font-size: 12px; }
.priority-card h3 { margin: 18px 0 9px; font-size: 17px; }
.priority-card p { flex: 1; margin: 0 0 18px; font-size: 13px; line-height: 1.8; color: var(--crm-text-secondary); }
.entry-link { color: var(--crm-color-brand); }
.secondary-panel, .demo-panel { margin-top: 16px; padding: 0 22px; }
summary { padding: 17px 0; cursor: pointer; font-size: 14px; font-weight: 600; }
summary span { margin-left: 12px; font-size: 12px; color: var(--crm-text-secondary); font-weight: 400; }
.other-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 2px 0 18px; }
.other-grid a { display: flex; align-items: center; gap: 8px; padding: 12px; color: var(--crm-text-main); background: var(--crm-bg-page); border-radius: 6px; font-size: 13px; }
.other-grid a .el-icon:last-child { margin-left: auto; }
.demo-note { color: var(--crm-text-secondary); font-size: 13px; line-height: 1.8; margin: 0 0 10px; }
.demo-steps { margin: 0; padding: 0 0 8px 22px; }
.demo-steps li { padding: 12px 0 12px 5px; border-top: 1px solid var(--crm-border); }
.demo-steps li::marker { color: var(--role-accent); font-weight: 600; }
.demo-steps strong { font-size: 14px; }
.demo-steps p { font-size: 13px; color: var(--crm-text-secondary); line-height: 1.8; margin: 6px 0; }
.workbench-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-top: 14px; color: var(--crm-text-secondary); font-size: 12px; }
.connection { display: flex; align-items: center; flex-wrap: wrap; overflow-wrap: anywhere; }
.connection-error { color: var(--crm-color-danger); }
.account-links { display: flex; gap: 18px; }
.priority-card:focus-visible, a:focus-visible, summary:focus-visible { outline: 2px solid var(--role-accent); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) { .priority-card { transition: none; } }
@media (max-width: 800px) { .priority-grid { grid-template-columns: 1fr; gap: 12px; } .priority-card { padding: 18px; } .priority-card h3 { margin-top: 12px; } .other-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .identity-strip { padding: 16px; gap: 10px; flex-wrap: wrap; } .identity-copy { flex-basis: calc(100% - 54px); } .identity-copy h2 span { display: block; margin: 5px 0 0; } .other-grid { grid-template-columns: 1fr; } .secondary-panel, .demo-panel { padding: 0 16px; } }
</style>
