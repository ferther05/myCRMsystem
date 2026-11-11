/* 文件作用：集中维护角色与业务页面的视觉标识，仅用于展示，不决定访问权限。 */
export const rolePresentation = {
  ADMIN: { title: '管理员', focus: '全局管理', scope: '全局业务', color: '#6550a3', soft: '#f1eef9', icon: 'Setting', hint: '账号分配、业务统筹与异常处理' },
  MANAGER: { title: '团队经理', focus: '团队管理', scope: '本团队业务', color: '#176f69', soft: '#eaf5f2', icon: 'OfficeBuilding', hint: '团队进展、跟进核验与业务审批' },
  SPECIALIST: { title: '销售专员', focus: '销售跟进', scope: '本人及授权业务', color: '#2459ab', soft: '#edf3fc', icon: 'User', hint: '客户跟进、商机推进与签约回款' },
};
const pages = {
  '/workbench': { label: '工作空间', color: '#385b8e', soft: '#eef3fa', icon: 'House' },
  '/customers': { label: '客户档案', color: '#2459ab', soft: '#edf3fc', icon: 'User' },
  '/public-pool': { label: '公海资源', color: '#146e79', soft: '#eaf5f7', icon: 'Connection' },
  '/opportunities': { label: '销售推进', color: '#8c5c16', soft: '#fbf3e5', icon: 'TrendCharts' },
  '/tasks': { label: '待办与追溯', color: '#3f6485', soft: '#eef4f8', icon: 'List' },
  '/analytics': { label: '经营分析', color: '#176f69', soft: '#eaf5f2', icon: 'DataAnalysis' },
  '/recycle': { label: '公海规则', color: '#146e79', soft: '#eaf5f7', icon: 'Refresh' },
  '/approvals': { label: '账号审批', color: '#6550a3', soft: '#f1eef9', icon: 'CircleCheck' },
  '/team': { label: '团队协同', color: '#176f69', soft: '#eaf5f2', icon: 'OfficeBuilding' },
  '/system': { label: '账号与权限', color: '#6550a3', soft: '#f1eef9', icon: 'Setting' },
  '/account': { label: '个人设置', color: '#385b8e', soft: '#eef3fa', icon: 'User' },
};
/** 根据当前路由取得视觉标识，未知页面使用中性色。 */
export const pagePresentation = (path) => pages[path] || pages['/workbench'];
