/* 文件作用：封装账号、团队和内部账号申请接口。 */
import { http } from './http';

/** 分页查询可见账号。 */
export const listUsers = (params) => http.get('/users', { params });
/** 读取账号详情和最新版本。 */
export const getUser = (id) => http.get(`/users/${id}`);
/** 查询可见的团队选项。 */
export const listTeams = () => http.get('/teams');
/** 管理员直接创建账号。 */
export const createUser = (payload) => http.post('/users', payload);
/** 调整账号角色、团队或启停状态。 */
export const changeAccess = (id, payload) => http.put(`/users/${id}/access`, payload);
/** 重置账号初始密码并撤销旧令牌。 */
export const resetPassword = (id, payload) => http.post(`/users/${id}/reset-password`, payload);
/** 分页查询内部账号申请。 */
export const listApplications = (params) => http.get('/account-applications', { params });
/** 管理员待核验账号申请数量。 */
export const applicationSummary = () => http.get('/account-applications/summary');
/** 提交内部账号申请。 */
export const applyAccount = (payload) => http.post('/account-applications', payload);
/** 审核通过或驳回账号申请。 */
export const reviewApplication = (id, payload) =>
  http.post(`/account-applications/${id}/review`, payload);
/** 重新提交被驳回的账号申请。 */
export const resubmitApplication = (id, version) =>
  http.post(`/account-applications/${id}/resubmit`, null, { params: { version } });
