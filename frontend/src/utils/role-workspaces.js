/* 文件作用：角色导航顺序与工作重点；访问权限继续复用canonical模块定义。 */
import { modules, canVisit } from '@/router/modules';

export const workspaces = {
  SPECIALIST: {
    focus: '维护客户关系，完成本阶段下一步',
    groups: [['工作空间', '/workbench'], ['常用销售', '/customers', '/opportunities', '/tasks'], ['资源与复盘', '/public-pool', '/analytics']],
    primary: ['/customers', '/opportunities'],
    entries: [
      { path: '/customers', title: '客户关系维护', icon: 'User', description: '查看联系人、近期互动和下一步维护计划。' },
      { path: '/opportunities', title: '推进我的商机', icon: 'TrendCharts', description: '补齐当前阶段要求，继续签约与回款。' },
      { path: '/public-pool', title: '领取公海资源', icon: 'Connection', description: '按领取规则拓展本人客户资源。' },
    ],
    categories: ['TODAY', 'RETURNED', 'OPEN'], defaultCategory: 'TODAY',
  },
  MANAGER: {
    focus: '核验关键材料，协调团队卡点',
    groups: [['工作空间', '/workbench'], ['团队推进', '/opportunities', '/tasks', '/analytics'], ['团队与资源', '/customers', '/team', '/public-pool', '/approvals']],
    primary: ['/opportunities'],
    entries: [
      { category: 'REVIEW', title: '待我核验', icon: 'CircleCheck', description: '核验本团队有效跟进、合同登记和最新签署文件。' },
      { path: '/opportunities', title: '协调团队商机', icon: 'TrendCharts', description: '查看阶段缺项、负责人和协作分工。' },
      { path: '/analytics', title: '复盘团队业绩', icon: 'DataAnalysis', description: '先看未收与逾期，再看阶段和人员表现。' },
    ],
    categories: ['REVIEW', 'APPROVAL'], attention: ['STAGNANT', 'OVERDUE'], defaultCategory: 'REVIEW',
  },
  ADMIN: {
    focus: '掌握经营结果，统筹资源与运行',
    groups: [['经营总览', '/workbench', '/analytics'], ['资源配置', '/customers', '/opportunities', '/public-pool'], ['系统治理', '/system', '/tasks', '/recycle']],
    primary: ['/analytics'],
    entries: [
      { path: '/analytics', title: '全局经营看板', icon: 'DataAnalysis', description: '查看签约、到账、未收和逾期，按需展开分析。' },
      { path: '/customers', title: '资源录入与分配', icon: 'User', description: '维护客户归属、录入资源或办理完整交接。' },
      { path: '/tasks?tab=jobs', title: '运行与审计', icon: 'List', description: '检查任务结果，追溯具体业务的操作变化。' },
    ],
    categories: ['JOB_FAILURE', 'ACCOUNTS', 'OVERDUE', 'REVIEW', 'APPROVAL'], defaultCategory: 'JOB_FAILURE',
  },
};

/** 角色配置只改变顺序和展示名，不能新增授权页面。 */
export function navigationFor(role) {
  const workspace = workspaces[role];
  if (!workspace) return [];
  return workspace.groups.map(([title, ...paths]) => ({ title, items: paths.map(path => modules.find(item => item.path === path))
    .filter(item => item && canVisit(item, role)).map(item => ({ ...item,
      title: item.path === '/approvals' ? (role === 'ADMIN' ? '账号申请审核' : '账号申请') : item.title,
      primary: workspace.primary.includes(item.path),
    })) }));
}

export const rolePageCopy = {
  SPECIALIST: { customers: ['我的客户关系', '先看联系人、近期互动与下一步计划，再查完整档案。'], opportunities: ['我的商机推进', '查看当前阶段缺项，办理跟进、报价、合同与回款。'], analytics: ['个人业务复盘', '先看阶段进展与客户关系，再复盘个人签约和回款。'] },
  MANAGER: { customers: ['团队客户维护', '查看负责人、客户关系与团队维护计划，协调业务跟进。'], opportunities: ['团队商机推进', '核验当前阶段材料，协调负责人和协作成员；阶段由系统推进。'], analytics: ['团队经营复盘', '关注团队应收与逾期，按需查看阶段卡点和人员表现。'] },
  ADMIN: { customers: ['客户资源与归属', '录入和分配客户，维护负责人及团队，必要时办理完整交接。'], opportunities: ['全局商机管理', '统筹业务归属、阶段进展与异常，管理纠正保留原因和记录。'], analytics: ['全局经营看板', '先看经营结果与资金风险，再按需查看漏斗、排行和过程质量。'] },
};
