// Routes and navigation share one role definition. Backend permissions remain authoritative.
export const modules = [
  { path: '/workbench', title: '工作台', icon: 'House', group: '工作空间', ready: true },
  { path: '/customers', ready: true, title: '客户管理', icon: 'User', group: '销售业务', description: '客户档案、联系人、分类标签和范围查询' },
  { path: '/public-pool', ready: true, title: '客户公海', icon: 'Connection', group: '销售业务', description: '公海基础信息脱敏查询' },
  { path: '/opportunities', ready: true, title: '商机管理', icon: 'TrendCharts', group: '销售业务', description: '从线索到赢单的阶段推进' },
  { path: '/contracts', ready: true, title: '合同登记', icon: 'Document', group: '销售业务', description: '合同登记、签署资料与经理核验' },
  { path: '/tasks', title: '任务与日志', icon: 'List', group: '协同管理', description: '待办、提醒和操作追溯' },
  { path: '/approvals', ready: true, title: '审批中心', icon: 'CircleCheck', group: '协同管理', roles: ['ADMIN', 'MANAGER'], description: '内部账号申请与审核' },
  { path: '/team', ready: true, title: '团队管理', icon: 'OfficeBuilding', group: '协同管理', roles: ['MANAGER'], description: '团队成员与归属管理' },
  { path: '/system', ready: true, title: '账号管理', icon: 'Setting', group: '协同管理', roles: ['ADMIN'], description: '账号、角色团队、启停与重置密码' },
  { path: '/analytics', ready: true, title: '数据看板', icon: 'DataAnalysis', group: '经营分析', description: '销售漏斗、业绩与回款分析' },
]

export const canVisit = (item, role) => !item.roles || item.roles.includes(role)
